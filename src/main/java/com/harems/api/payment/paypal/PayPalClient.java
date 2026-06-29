package com.harems.api.payment.paypal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Slf4j
public class PayPalClient {

    private final PayPalProperties props;
    private final RestClient restClient;

    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    public PayPalClient(PayPalProperties props) {
        this.props = props;
        this.restClient = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .build();
        log.info("PayPal mode={} baseUrl={} clientIdConfigured={} webhookIdConfigured={} productIdConfigured={} plansConfigured={}",
                props.getMode(),
                props.getBaseUrl(),
                !props.getClientId().isBlank(),
                !props.getWebhookId().isBlank(),
                !props.getProductId().isBlank(),
                (!props.getPremiumPlanId().isBlank() && !props.getVipPlanId().isBlank()));
    }

    synchronized String accessToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(60))) {
            return cachedToken;
        }
        if (props.getClientId().isBlank()) {
            throw new PayPalException("PAYPAL_CLIENT_ID no configurado en las variables de entorno.", 503);
        }
        if (props.getClientSecret().isBlank()) {
            throw new PayPalException("PAYPAL_CLIENT_SECRET no configurado en las variables de entorno.", 503);
        }
        String encoded = Base64.getEncoder().encodeToString(
                (props.getClientId() + ":" + props.getClientSecret())
                        .getBytes(StandardCharsets.UTF_8));
        try {
            JsonNode json = restClient.post()
                    .uri("/v1/oauth2/token")
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + encoded)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("grant_type=client_credentials")
                    .retrieve()
                    .body(JsonNode.class);

            if (json == null) throw new PayPalException("Respuesta vacía al obtener token", 502);
            cachedToken = json.get("access_token").asText();
            long expiresIn = json.path("expires_in").asLong(3600);
            tokenExpiresAt = Instant.now().plusSeconds(expiresIn);
            return cachedToken;
        } catch (RestClientResponseException e) {
            throw new PayPalException("Error de autenticación con PayPal: " + e.getStatusCode(), e.getStatusCode().value());
        } catch (PayPalException e) {
            throw e;
        } catch (Exception e) {
            throw new PayPalException("Error de comunicación con PayPal: " + e.getMessage(), 502);
        }
    }

    public JsonNode createSubscription(Map<String, Object> requestBody) {
        try {
            JsonNode response = restClient.post()
                    .uri("/v1/billing/subscriptions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) throw new PayPalException("Respuesta vacía al crear suscripción", 502);
            return response;
        } catch (RestClientResponseException e) {
            log.error("PayPal create-subscription error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PayPalException("Error creando suscripción en PayPal", e.getStatusCode().value());
        } catch (PayPalException e) {
            throw e;
        } catch (Exception e) {
            throw new PayPalException("Error creando suscripción: " + e.getMessage(), 502);
        }
    }

    public JsonNode getSubscription(String subscriptionId) {
        try {
            JsonNode response = restClient.get()
                    .uri("/v1/billing/subscriptions/" + subscriptionId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) throw new PayPalException("Respuesta vacía al consultar suscripción", 502);
            return response;
        } catch (RestClientResponseException e) {
            log.error("PayPal get-subscription error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PayPalException("Suscripción no encontrada en PayPal", e.getStatusCode().value());
        } catch (PayPalException e) {
            throw e;
        } catch (Exception e) {
            throw new PayPalException("Error consultando suscripción: " + e.getMessage(), 502);
        }
    }

    public boolean verifyWebhookSignature(String transmissionId, String transmissionTime,
                                           String certUrl, String authAlgo, String transmissionSig,
                                           String webhookId, JsonNode webhookEvent) {
        try {
            Map<String, Object> verifyBody = new LinkedHashMap<>();
            verifyBody.put("auth_algo", authAlgo);
            verifyBody.put("cert_url", certUrl);
            verifyBody.put("transmission_id", transmissionId);
            verifyBody.put("transmission_sig", transmissionSig);
            verifyBody.put("transmission_time", transmissionTime);
            verifyBody.put("webhook_id", webhookId);
            verifyBody.put("webhook_event", webhookEvent);

            JsonNode response = restClient.post()
                    .uri("/v1/notifications/verify-webhook-signature")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(verifyBody)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) return false;
            return "SUCCESS".equals(response.path("verification_status").asText());
        } catch (RestClientResponseException e) {
            log.warn("PayPal webhook verify error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PayPalException("Error verificando webhook", e.getStatusCode().value());
        } catch (PayPalException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error verificando firma webhook: {}", e.getMessage());
            return false;
        }
    }

    public void cancelSubscription(String subscriptionId, String reason) {
        try {
            restClient.post()
                    .uri("/v1/billing/subscriptions/" + subscriptionId + "/cancel")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("reason", reason))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            log.error("PayPal cancel-subscription error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PayPalException("Error cancelando suscripción en PayPal", e.getStatusCode().value());
        } catch (PayPalException e) {
            throw e;
        } catch (Exception e) {
            throw new PayPalException("Error cancelando suscripción: " + e.getMessage(), 502);
        }
    }
}
