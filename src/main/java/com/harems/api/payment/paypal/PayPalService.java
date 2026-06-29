package com.harems.api.payment.paypal;

import com.harems.api.common.exception.BadRequestException;
import com.harems.api.common.exception.ResourceNotFoundException;
import com.harems.api.payment.Payment;
import com.harems.api.payment.PaymentRepository;
import com.harems.api.payment.PaymentStatus;
import com.harems.api.payment.WebhookEvent;
import com.harems.api.payment.WebhookEventRepository;
import com.harems.api.payment.paypal.dto.CreateSubscriptionResponse;
import com.harems.api.profile.Profile;
import com.harems.api.profile.ProfileRepository;
import com.harems.api.profile.ProfileService;
import com.harems.api.subscription.*;
import com.harems.api.subscription.dto.SubscriptionResponse;
import com.harems.api.user.User;
import com.harems.api.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PayPalService {

    private final PayPalProperties props;
    private final PayPalClient payPalClient;
    private final PaymentRepository paymentRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ProfileService profileService;
    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    // ─── Create subscription ──────────────────────────────────────────────────

    public CreateSubscriptionResponse createSubscription(User user, PlanType plan) {
        String paypalPlanId = resolvePlanId(plan);
        String externalRef = buildExternalRef(user.getId(), plan);

        Map<String, Object> subRequest = buildSubscriptionRequest(paypalPlanId, user, externalRef);
        JsonNode paypalResponse = payPalClient.createSubscription(subRequest);

        String subscriptionId = paypalResponse.path("id").asText();
        String approvalUrl = findApprovalUrl(paypalResponse);
        String rawStatus = paypalResponse.path("status").asText("APPROVAL_PENDING");

        Payment payment = Payment.builder()
                .user(user)
                .provider("PAYPAL")
                .plan(plan)
                .status(PaymentStatus.CREATED)
                .paypalSubscriptionId(subscriptionId)
                .paypalPlanId(paypalPlanId)
                .externalReference(externalRef)
                .rawStatus(rawStatus)
                .build();
        paymentRepository.save(payment);

        return CreateSubscriptionResponse.builder()
                .provider("PAYPAL")
                .plan(plan)
                .paypalPlanId(paypalPlanId)
                .paypalSubscriptionId(subscriptionId)
                .approvalUrl(approvalUrl)
                .status("PENDING")
                .build();
    }

    // ─── Confirm subscription (called after PayPal redirects back) ────────────

    public SubscriptionResponse confirmSubscription(User user, String paypalSubscriptionId) {
        JsonNode paypalSub = payPalClient.getSubscription(paypalSubscriptionId);
        String paypalStatus = paypalSub.path("status").asText();
        String paypalPlanId = paypalSub.path("plan_id").asText();

        Payment payment = paymentRepository.findByPaypalSubscriptionId(paypalSubscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Suscripción no encontrada. Contacta a soporte si completaste el pago."));

        if (!payment.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Esta suscripción no pertenece a tu cuenta.");
        }

        String expectedPlanId = resolvePlanId(payment.getPlan());
        if (!paypalPlanId.isBlank() && !expectedPlanId.equals(paypalPlanId)) {
            log.warn("Plan ID mismatch para {}: esperado={}, recibido={}",
                    paypalSubscriptionId, expectedPlanId, paypalPlanId);
        }

        payment.setRawStatus(paypalStatus);

        if ("ACTIVE".equals(paypalStatus)) {
            payment.setStatus(PaymentStatus.ACTIVE);
            paymentRepository.save(payment);
            activatePlan(user, payment.getPlan(), paypalSubscriptionId, paypalStatus);
        } else {
            payment.setStatus(PaymentStatus.PENDING);
            paymentRepository.save(payment);
            upsertSubscription(user, payment.getPlan(), paypalSubscriptionId, paypalStatus, SubscriptionStatus.PENDING);
        }

        Profile profile = profileService.getProfile(user);
        profileService.resolveEffectivePlan(profile);
        return buildResponse(profile);
    }

    // ─── Webhook processing ───────────────────────────────────────────────────

    public void processWebhook(HttpHeaders headers, String rawBody) {
        String transmissionId = header(headers, "paypal-transmission-id");
        String transmissionTime = header(headers, "paypal-transmission-time");
        String certUrl = header(headers, "paypal-cert-url");
        String authAlgo = header(headers, "paypal-auth-algo");
        String transmissionSig = header(headers, "paypal-transmission-sig");

        JsonNode event;
        try {
            event = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            throw new BadRequestException("Payload del webhook inválido.");
        }

        String eventId = event.path("id").asText();
        String eventType = event.path("event_type").asText();

        if (!payPalClient.verifyWebhookSignature(
                transmissionId, transmissionTime, certUrl, authAlgo, transmissionSig,
                props.getWebhookId(), event)) {
            log.warn("Webhook {} rechazado: firma inválida", eventId);
            throw new PayPalException("Firma de webhook inválida.", 401);
        }

        if (webhookEventRepository.existsByEventId(eventId)) {
            log.info("Webhook {} ya procesado, ignorando", eventId);
            return;
        }

        JsonNode resource = event.path("resource");
        WebhookEvent webhookEvent = WebhookEvent.builder()
                .provider("PAYPAL")
                .eventId(eventId)
                .eventType(eventType)
                .resourceId(resource.path("id").asText())
                .payload(rawBody)
                .build();
        webhookEventRepository.save(webhookEvent);

        try {
            handleEvent(eventType, resource);
            webhookEvent.setProcessed(true);
            webhookEvent.setProcessedAt(LocalDateTime.now());
        } catch (Exception e) {
            log.error("Error procesando webhook {} ({}): {}", eventId, eventType, e.getMessage(), e);
        }
        webhookEventRepository.save(webhookEvent);
    }

    // ─── Cancel subscription ──────────────────────────────────────────────────

    public void cancelSubscription(User user) {
        Payment payment = paymentRepository
                .findTopByUserAndStatusOrderByCreatedAtDesc(user, PaymentStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("No tienes una suscripción activa para cancelar."));

        payPalClient.cancelSubscription(payment.getPaypalSubscriptionId(),
                "Cancelación solicitada por el usuario");

        payment.setStatus(PaymentStatus.CANCEL_PENDING);
        payment.setRawStatus("CANCEL_PENDING");
        paymentRepository.save(payment);

        subscriptionRepository.findByPaypalSubscriptionId(payment.getPaypalSubscriptionId())
                .ifPresent(sub -> {
                    sub.setStatus(SubscriptionStatus.CANCELLED);
                    sub.setRawStatus("CANCEL_PENDING");
                    subscriptionRepository.save(sub);
                });
    }

    // ─── Event handlers ───────────────────────────────────────────────────────

    private void handleEvent(String eventType, JsonNode resource) {
        String subscriptionId = resource.path("id").asText();
        String rawStatus = resource.path("status").asText();

        switch (eventType) {
            case "BILLING.SUBSCRIPTION.ACTIVATED" ->
                    handleActivated(subscriptionId, rawStatus, resource);
            case "BILLING.SUBSCRIPTION.CANCELLED", "BILLING.SUBSCRIPTION.EXPIRED" ->
                    handleTerminated(subscriptionId, rawStatus, SubscriptionStatus.CANCELLED, PaymentStatus.CANCELLED);
            case "BILLING.SUBSCRIPTION.SUSPENDED" ->
                    handleTerminated(subscriptionId, rawStatus, SubscriptionStatus.SUSPENDED, PaymentStatus.SUSPENDED);
            case "PAYMENT.SALE.COMPLETED" ->
                    log.info("Pago recibido — billing_agreement_id={}",
                            resource.path("billing_agreement_id").asText());
            case "PAYMENT.SALE.REFUNDED" ->
                    log.info("Reembolso recibido — billing_agreement_id={}",
                            resource.path("billing_agreement_id").asText());
            default -> log.debug("Evento PayPal no procesado: {}", eventType);
        }
    }

    private void handleActivated(String subscriptionId, String rawStatus, JsonNode resource) {
        Payment payment = paymentRepository.findByPaypalSubscriptionId(subscriptionId)
                .orElseGet(() -> resolveFromWebhook(resource, subscriptionId));

        if (payment == null) {
            log.error("No se pudo resolver Payment para subscriptionId={}", subscriptionId);
            return;
        }

        boolean alreadyActive = payment.getStatus() == PaymentStatus.ACTIVE;
        payment.setStatus(PaymentStatus.ACTIVE);
        payment.setRawStatus(rawStatus);
        paymentRepository.save(payment);

        if (alreadyActive) {
            // El plan ya fue activado (por confirm-subscription). No reasignar créditos para no resetear los usados.
            log.info("Webhook ACTIVATED para subscriptionId={} ya procesado, omitiendo reasignación de créditos.", subscriptionId);
            return;
        }

        activatePlan(payment.getUser(), payment.getPlan(), subscriptionId, rawStatus);
    }

    private void handleTerminated(String subscriptionId, String rawStatus,
                                   SubscriptionStatus newSubStatus, PaymentStatus newPayStatus) {
        paymentRepository.findByPaypalSubscriptionId(subscriptionId).ifPresent(payment -> {
            payment.setStatus(newPayStatus);
            payment.setRawStatus(rawStatus);
            paymentRepository.save(payment);

            subscriptionRepository.findByPaypalSubscriptionId(subscriptionId).ifPresent(sub -> {
                sub.setStatus(newSubStatus);
                sub.setRawStatus(rawStatus);
                subscriptionRepository.save(sub);
            });

            if (newPayStatus == PaymentStatus.CANCELLED || newPayStatus == PaymentStatus.EXPIRED) {
                try {
                    Profile profile = profileService.getProfile(payment.getUser());
                    if (profile.getPlan() != PlanType.FREE) {
                        profileService.applyPlan(profile, PlanType.FREE);
                    }
                } catch (Exception e) {
                    log.error("Error degradando a FREE tras terminación {}: {}", subscriptionId, e.getMessage());
                }
            }
        });
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private void activatePlan(User user, PlanType plan, String paypalSubscriptionId, String rawStatus) {
        Profile profile = profileService.getProfile(user);
        profileService.applyPlan(profile, plan);
        upsertSubscription(user, plan, paypalSubscriptionId, rawStatus, SubscriptionStatus.ACTIVE);
    }

    private void upsertSubscription(User user, PlanType plan, String paypalSubscriptionId,
                                     String rawStatus, SubscriptionStatus status) {
        Subscription sub = subscriptionRepository.findByPaypalSubscriptionId(paypalSubscriptionId)
                .orElse(Subscription.builder()
                        .user(user)
                        .plan(plan)
                        .paypalSubscriptionId(paypalSubscriptionId)
                        .paymentReference(paypalSubscriptionId)
                        .startsAt(LocalDateTime.now())
                        .build());

        sub.setPlan(plan);
        sub.setStatus(status);
        sub.setRawStatus(rawStatus);

        if (status == SubscriptionStatus.ACTIVE) {
            sub.setStartsAt(LocalDateTime.now());
            Integer days = PlanBenefits.durationDaysFor(plan);
            sub.setEndsAt(days != null ? LocalDateTime.now().plusDays(days) : null);
        }

        subscriptionRepository.save(sub);
    }

    private Payment resolveFromWebhook(JsonNode resource, String subscriptionId) {
        String customId = resource.path("custom_id").asText("");
        if (customId.isBlank()) {
            log.warn("No hay custom_id en webhook para subscriptionId={}", subscriptionId);
            return null;
        }
        try {
            Long userId = extractLong(customId, "user");
            PlanType plan = PlanType.valueOf(extractString(customId, "plan"));
            String planId = resource.path("plan_id").asText();

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + userId));

            Payment p = Payment.builder()
                    .user(user)
                    .provider("PAYPAL")
                    .plan(plan)
                    .status(PaymentStatus.CREATED)
                    .paypalSubscriptionId(subscriptionId)
                    .paypalPlanId(planId)
                    .externalReference(customId)
                    .rawStatus("RESOLVED_FROM_WEBHOOK")
                    .build();
            return paymentRepository.save(p);
        } catch (Exception e) {
            log.error("Error resolviendo pago desde custom_id {}: {}", customId, e.getMessage());
            return null;
        }
    }

    private String resolvePlanId(PlanType plan) {
        String id = switch (plan) {
            case PREMIUM -> props.getPremiumPlanId();
            case VIP -> props.getVipPlanId();
            case TRIAL_3_DAYS -> props.getTrialPlanId();
            default -> throw new BadRequestException("Plan no soportado para pago: " + plan);
        };
        if (id == null || id.isBlank()) {
            throw new PayPalException("Plan de PayPal no configurado para: " + plan, 503);
        }
        return id;
    }

    private String buildExternalRef(Long userId, PlanType plan) {
        return String.format("user:%d:plan:%s:ts:%d", userId, plan.name(), System.currentTimeMillis());
    }

    private Map<String, Object> buildSubscriptionRequest(String paypalPlanId, User user, String externalRef) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("plan_id", paypalPlanId);
        req.put("custom_id", externalRef);

        String firstName = user.getName().split(" ")[0];
        Map<String, Object> subscriber = new LinkedHashMap<>();
        subscriber.put("name", Map.of("given_name", firstName));
        subscriber.put("email_address", user.getEmail());
        req.put("subscriber", subscriber);

        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("brand_name", "HAREMS");
        ctx.put("locale", "es-MX");
        ctx.put("shipping_preference", "NO_SHIPPING");
        ctx.put("user_action", "SUBSCRIBE_NOW");
        ctx.put("return_url", props.getSuccessUrl());
        ctx.put("cancel_url", props.getCancelUrl());
        req.put("application_context", ctx);

        return req;
    }

    private String findApprovalUrl(JsonNode response) {
        JsonNode links = response.path("links");
        if (links.isArray()) {
            for (JsonNode link : links) {
                if ("approve".equals(link.path("rel").asText())) {
                    return link.path("href").asText();
                }
            }
        }
        throw new PayPalException("No se encontró URL de aprobación en la respuesta de PayPal.", 502);
    }

    private SubscriptionResponse buildResponse(Profile profile) {
        return new SubscriptionResponse(
                profile.getPlan(),
                SubscriptionStatus.ACTIVE,
                profile.getPlanExpiresAt(),
                profile.getImageCredits(),
                profile.getMessagesUsed()
        );
    }

    private String header(HttpHeaders headers, String name) {
        String v = headers.getFirst(name);
        return v != null ? v : "";
    }

    private Long extractLong(String customId, String key) {
        return Long.parseLong(extractString(customId, key));
    }

    private String extractString(String customId, String key) {
        String[] parts = customId.split(":");
        for (int i = 0; i < parts.length - 1; i++) {
            if (key.equals(parts[i])) return parts[i + 1];
        }
        throw new IllegalArgumentException("No se encontró '" + key + "' en: " + customId);
    }
}
