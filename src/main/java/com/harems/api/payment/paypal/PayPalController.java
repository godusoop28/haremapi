package com.harems.api.payment.paypal;

import com.harems.api.payment.BillingProperties;
import com.harems.api.payment.paypal.dto.ConfirmSubscriptionRequest;
import com.harems.api.payment.paypal.dto.CreateSubscriptionRequest;
import com.harems.api.payment.paypal.dto.CreateSubscriptionResponse;
import com.harems.api.security.UserPrincipal;
import com.harems.api.subscription.dto.SubscriptionResponse;
import com.harems.api.user.Role;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/payments/paypal")
@RequiredArgsConstructor
public class PayPalController {

    private final PayPalService payPalService;
    private final PayPalClient payPalClient;
    private final PayPalProperties payPalProperties;
    private final BillingProperties billingProperties;

    @PostMapping("/create-subscription")
    public CreateSubscriptionResponse createSubscription(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateSubscriptionRequest request) {
        return payPalService.createSubscription(principal.getUser(), request.plan());
    }

    @PostMapping("/confirm-subscription")
    public SubscriptionResponse confirmSubscription(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ConfirmSubscriptionRequest request) {
        return payPalService.confirmSubscription(principal.getUser(), request.subscriptionId());
    }

    /**
     * Public endpoint — PayPal sends webhook events here.
     * Signature is verified server-side using PAYPAL_WEBHOOK_ID.
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestHeader HttpHeaders headers,
            @RequestBody String rawBody) {
        payPalService.processWebhook(headers, rawBody);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/cancel-subscription")
    public ResponseEntity<Void> cancelSubscription(
            @AuthenticationPrincipal UserPrincipal principal) {
        payPalService.cancelSubscription(principal.getUser());
        return ResponseEntity.ok().build();
    }

    /**
     * Intenta obtener un token OAuth de PayPal y reporta el resultado. Solo ADMIN.
     * No devuelve secretos ni el token — solo estado de configuración y si OAuth funciona.
     */
    @GetMapping("/test-auth")
    public ResponseEntity<Map<String, Object>> testAuth(
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal.getUser().getRole() != Role.ADMIN) {
            throw new AccessDeniedException("No tienes permisos para realizar esta acción.");
        }

        boolean clientIdConfigured = !payPalProperties.getClientId().isBlank();
        boolean clientSecretConfigured = !payPalProperties.getClientSecret().isBlank();

        boolean oauthOk = false;
        String error = null;
        try {
            payPalClient.accessToken();
            oauthOk = true;
        } catch (PayPalException e) {
            error = e.getMessage();
        } catch (Exception e) {
            error = "Error inesperado: " + e.getMessage();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", payPalProperties.getMode());
        result.put("baseUrl", payPalProperties.getBaseUrl());
        result.put("clientIdConfigured", clientIdConfigured);
        result.put("clientSecretConfigured", clientSecretConfigured);
        result.put("clientIdLength", payPalProperties.getClientId().length());
        result.put("clientSecretLength", payPalProperties.getClientSecret().length());
        result.put("productIdConfigured", payPalProperties.isProductIdEffectivelyConfigured());
        result.put("oauthOk", oauthOk);
        if (error != null) {
            result.put("error", error);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Diagnóstico de configuración PayPal. Solo ADMIN.
     * No expone secretos reales — solo indica si están configurados.
     */
    @GetMapping("/readiness")
    public ResponseEntity<Map<String, Object>> readiness(
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal.getUser().getRole() != Role.ADMIN) {
            throw new AccessDeniedException("No tienes permisos para realizar esta acción.");
        }

        boolean clientIdOk = !payPalProperties.getClientId().isBlank();
        boolean clientSecretOk = !payPalProperties.getClientSecret().isBlank();
        boolean webhookIdOk = !payPalProperties.getWebhookId().isBlank();
        boolean productIdOk = !payPalProperties.getProductId().isBlank();
        boolean trialPlanOk = !payPalProperties.getTrialPlanId().isBlank();
        boolean premiumPlanOk = !payPalProperties.getPremiumPlanId().isBlank();
        boolean vipPlanOk = !payPalProperties.getVipPlanId().isBlank();

        List<String> missing = new ArrayList<>();
        if (!clientIdOk) missing.add("PAYPAL_CLIENT_ID");
        if (!clientSecretOk) missing.add("PAYPAL_CLIENT_SECRET");
        if (!webhookIdOk) missing.add("PAYPAL_WEBHOOK_ID");
        if (!productIdOk) missing.add("PAYPAL_PRODUCT_ID");
        if (!premiumPlanOk) missing.add("PAYPAL_PREMIUM_PLAN_ID");
        if (!vipPlanOk) missing.add("PAYPAL_VIP_PLAN_ID");
        if (!trialPlanOk) missing.add("PAYPAL_TRIAL_PLAN_ID (opcional — ocultar plan Trial si falta)");

        boolean ready = missing.isEmpty() || (missing.size() == 1
                && missing.get(0).startsWith("PAYPAL_TRIAL_PLAN_ID"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("billingProvider", billingProperties.getProvider());
        result.put("mode", payPalProperties.getMode());
        result.put("clientIdConfigured", clientIdOk);
        result.put("clientSecretConfigured", clientSecretOk);
        result.put("webhookIdConfigured", webhookIdOk);
        result.put("productIdConfigured", productIdOk);
        result.put("trialPlanIdConfigured", trialPlanOk);
        result.put("premiumPlanIdConfigured", premiumPlanOk);
        result.put("vipPlanIdConfigured", vipPlanOk);
        result.put("successUrl", payPalProperties.getSuccessUrl());
        result.put("cancelUrl", payPalProperties.getCancelUrl());
        result.put("webhookUrl", payPalProperties.getWebhookUrl());
        result.put("ready", ready);
        result.put("missing", missing);

        return ResponseEntity.ok(result);
    }
}
