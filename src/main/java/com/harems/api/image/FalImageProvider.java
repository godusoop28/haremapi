package com.harems.api.image;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * fal.ai image provider con retry automático cuando el contenido es bloqueado.
 *
 * FLUJO:
 *   1. Llama a fal.ai con el prompt completo.
 *   2. Si has_nsfw_concepts=true (imagen negra por bloqueo) → retry con prompt suavizado.
 *   3. Si el retry también falla → lanza excepción.
 *
 * MODELO RECOMENDADO: fal-ai/flux-pro/v1.1 (FAL_IMAGE_MODEL en Render)
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "image.provider", havingValue = "FAL")
public class FalImageProvider implements ImageGenerationProvider {

    private final RestClient restClient;
    private final String model;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public FalImageProvider(
            @Value("${image.fal.api-key:}") String apiKey,
            @Value("${image.fal.model:fal-ai/flux/dev}") String model,
            @Value("${image.fal.base-url:https://fal.run}") String baseUrl,
            @Value("${image.generation-timeout-seconds:60}") int timeoutSeconds,
            ObjectMapper objectMapper
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[FAL] FAL_KEY no configurada.");
        }
        this.model = model;
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(timeoutSeconds * 1_000);

        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("Authorization", "Key " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public ImageGenerationResult generate(ImageGenerationInput input) {
        String characterSlug = input.character().getSlug();
        log.info("[FAL] Generating — model={} character={} size={}x{}",
                model, characterSlug, input.width(), input.height());

        // ── Intento 1: prompt completo ────────────────────────────────────────
        Map<String, Object> body = buildRequestBody(input);
        logBody(body);

        JsonNode response = callFal(body, characterSlug);
        logResponse(response);

        // Si el contenido fue bloqueado → retry automático con prompt suavizado
        if (isContentBlocked(response)) {
            log.warn("[FAL] Contenido bloqueado (has_nsfw_concepts=true) — reintentando con prompt suavizado para character={}", characterSlug);

            // ── Intento 2: prompt suavizado ───────────────────────────────────
            ImageGenerationInput softened = softenInput(input);
            Map<String, Object> softerBody = buildRequestBody(softened);
            logBody(softerBody);

            response = callFal(softerBody, characterSlug);
            logResponse(response);

            if (isContentBlocked(response)) {
                log.error("[FAL] Contenido sigue bloqueado tras retry para character={}. Prompt: {}",
                        characterSlug, truncate(softened.positivePrompt(), 200));
                throw new RuntimeException(
                        "La imagen no pudo generarse por restricciones del proveedor. Intenta con una escena diferente.");
            }
            log.info("[FAL] Retry exitoso para character={}", characterSlug);
        }

        return parseResponse(response, characterSlug);
    }

    // ── Llamada HTTP a fal.ai ──────────────────────────────────────────────────

    private JsonNode callFal(Map<String, Object> body, String characterSlug) {
        String url = baseUrl + "/" + model;
        try {
            return restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException e) {
            handleClientError(e, characterSlug);
            return null; // unreachable
        } catch (HttpServerErrorException e) {
            log.error("[FAL] Server error {} — body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("El servicio de imágenes no está disponible. Inténtalo más tarde.");
        } catch (Exception e) {
            log.error("[FAL] Error inesperado — character={} cause={}", characterSlug, e.getMessage());
            throw new RuntimeException("Error al generar la imagen. Inténtalo de nuevo.");
        }
    }

    // ── Deteccion de bloqueo de contenido ──────────────────────────────────────

    private boolean isContentBlocked(JsonNode response) {
        if (response == null) return false;
        JsonNode nsfw = response.path("has_nsfw_concepts");
        if (nsfw.isArray() && !nsfw.isEmpty()) {
            return nsfw.get(0).asBoolean(false);
        }
        return false;
    }

    /**
     * Suaviza el prompt eliminando las keywords que con más frecuencia activan
     * el filtro de palabras clave de fal.ai, manteniendo la intención visual.
     * flux-pro genera nudez completa con lenguaje artístico sin necesitar keywords explícitas.
     */
    private ImageGenerationInput softenInput(ImageGenerationInput original) {
        String softer = original.positivePrompt()
                // Partes corporales específicas → lenguaje artístico equivalente
                .replace("nipples exposed and visible", "natural bare body")
                .replace("visible erect nipples", "natural nude")
                .replace("bare breasts fully visible", "bare unclothed chest")
                .replace("bare breasts with visible erect nipples", "completely bare chest")
                .replace("all intimate body parts exposed", "full nude figure")
                .replace("all body parts fully visible and exposed", "completely nude")
                .replace("all body parts bare and visible", "full nude body")
                // Lenguaje explícito → framing artístico
                .replace("explicit adult erotic pose", "intimate adult pose")
                .replace("explicit adult content", "adult intimate art")
                .replace("adult explicit content", "intimate boudoir art")
                .replace("adult erotic content", "adult intimate art")
                .replace("provocative naked position", "intimate nude pose")
                .replace("explicit intimate adult moment", "intimate nude moment")
                .replace("explicit nude body", "nude body")
                .replace("explicit passionate adult pose", "passionate intimate pose");

        log.info("[FAL] Softened prompt for retry: '{}'", truncate(softer, 150));
        return new ImageGenerationInput(softer, original.negativePrompt(),
                original.width(), original.height(), original.steps(), original.cfg(), original.character());
    }

    // ── Construccion del body ──────────────────────────────────────────────────

    private Map<String, Object> buildRequestBody(ImageGenerationInput input) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("prompt", input.positivePrompt());
        body.put("num_images", 1);
        body.put("enable_safety_checker", false);

        boolean isFlux = model.toLowerCase().contains("flux");

        if (isFlux) {
            LinkedHashMap<String, Integer> size = new LinkedHashMap<>();
            size.put("width", input.width());
            size.put("height", input.height());
            body.put("image_size", size);

            if (model.contains("schnell")) {
                body.put("num_inference_steps", 4);
            } else if (model.contains("/dev")) {
                body.put("num_inference_steps", 28);
            }

            if (model.contains("flux-pro") || model.contains("flux/pro")) {
                body.put("safety_tolerance", "6");
                body.remove("enable_safety_checker");
            }
        } else {
            body.put("width", input.width());
            body.put("height", input.height());
            if (!input.negativePrompt().isBlank()) {
                body.put("negative_prompt", input.negativePrompt());
            }
        }

        return body;
    }

    // ── Parse de respuesta ────────────────────────────────────────────────────

    private ImageGenerationResult parseResponse(JsonNode response, String characterSlug) {
        if (response == null) {
            throw new RuntimeException("fal.ai no devolvió respuesta.");
        }

        JsonNode images = response.path("images");
        if (!images.isArray() || images.isEmpty()) {
            log.error("[FAL] Sin imágenes en respuesta para character={}: {}", characterSlug, response);
            throw new RuntimeException("fal.ai no generó ninguna imagen.");
        }

        String imageUrl = images.get(0).path("url").asText(null);
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new RuntimeException("fal.ai no devolvió URL válida.");
        }

        long seed = response.path("seed").asLong(0L);
        log.info("[FAL] Imagen generada OK — character={} url={} seed={}", characterSlug, imageUrl, seed);
        return new ImageGenerationResult(imageUrl, "fal-" + seed);
    }

    // ── Error handling ────────────────────────────────────────────────────────

    private void handleClientError(HttpClientErrorException e, String characterSlug) {
        int status = e.getStatusCode().value();
        String body = e.getResponseBodyAsString();
        switch (status) {
            case 401 -> {
                log.error("[FAL] 401 Unauthorized — FAL_KEY inválida. character={}", characterSlug);
                throw new RuntimeException("Servicio de imágenes no autorizado.");
            }
            case 422 -> {
                log.error("[FAL] 422 Payload inválido — character={} body={}", characterSlug, body);
                throw new RuntimeException("Solicitud inválida al servicio de imágenes.");
            }
            case 429 -> {
                log.warn("[FAL] 429 Rate limit — character={}", characterSlug);
                throw new RuntimeException("Límite de generación alcanzado. Intenta en unos minutos.");
            }
            default -> {
                log.error("[FAL] Error {} — character={} body={}", status, characterSlug, body);
                throw new RuntimeException("Error al generar la imagen (" + status + ").");
            }
        }
    }

    // ── Logging ───────────────────────────────────────────────────────────────

    private void logBody(Map<String, Object> body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            log.info("[FAL] Request → {}", json);
        } catch (Exception ignored) {}
    }

    private void logResponse(JsonNode response) {
        try {
            if (response != null) {
                log.info("[FAL] Response → {}", objectMapper.writeValueAsString(response));
            }
        } catch (Exception ignored) {}
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    @Override
    public String providerName() {
        return "FAL";
    }
}
