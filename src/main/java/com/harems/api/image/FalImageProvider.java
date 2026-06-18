package com.harems.api.image;

import com.harems.api.common.exception.ImageGenerationBlockedException;
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

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * fal.ai image provider con selección de modelo por AdultLevel.
 *
 * SELECCIÓN DE MODELO:
 *   SAFE     → FAL_IMAGE_MODEL_SAFE     (default: fal-ai/flux/schnell)
 *   SENSUAL  → FAL_IMAGE_MODEL_SENSUAL  (default: fal-ai/flux-pro/v1.1)
 *   NUDE     → FAL_IMAGE_MODEL_NUDE     (default: fal-ai/flux/dev)
 *   EXPLICIT → FAL_IMAGE_MODEL_EXPLICIT (default: fal-ai/flux/dev)
 *
 * Si FAL_VALIDATE_OUTPUT=true: verifica que la imagen no sea negra antes de devolver URL.
 * Si contenido bloqueado y FAL_REFUND_ON_BLOCKED=true: lanza ImageGenerationBlockedException
 *   para que ImageGenerationService reembolse créditos.
 *
 * NOTA: fal-ai/fast-sdxl tiene filtro NSFW hardcodeado — producirá imagen negra para
 *       contenido adulto. Usar fal-ai/flux/dev o fal-ai/flux-pro/v1.1 para NUDE/EXPLICIT.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "image.provider", havingValue = "FAL")
public class FalImageProvider implements ImageGenerationProvider {

    private final RestClient restClient;
    private final String defaultModel;
    private final String baseUrl;
    private final String safetyTolerance;
    private final int maxRetries;
    private final boolean refundOnBlocked;
    private final boolean validateOutput;
    private final ObjectMapper objectMapper;

    // Modelos por nivel
    private final String modelSafe;
    private final String modelSensual;
    private final String modelNude;
    private final String modelExplicit;
    private final String modelNudeFallback;

    public FalImageProvider(
            @Value("${image.fal.api-key:}") String apiKey,
            @Value("${image.fal.model:fal-ai/flux-pro/v1.1}") String defaultModel,
            @Value("${image.fal.base-url:https://fal.run}") String baseUrl,
            @Value("${image.fal.model-safe:fal-ai/flux/schnell}") String modelSafe,
            @Value("${image.fal.model-sensual:fal-ai/flux-pro/v1.1}") String modelSensual,
            @Value("${image.fal.model-nude:fal-ai/flux/dev}") String modelNude,
            @Value("${image.fal.model-explicit:fal-ai/flux/dev}") String modelExplicit,
            @Value("${image.fal.model-nude-fallback:fal-ai/flux/dev}") String modelNudeFallback,
            @Value("${image.fal.safety-tolerance:6}") String safetyTolerance,
            @Value("${image.fal.max-retries:2}") int maxRetries,
            @Value("${image.fal.refund-on-blocked:true}") boolean refundOnBlocked,
            @Value("${image.fal.validate-output:true}") boolean validateOutput,
            @Value("${image.generation-timeout-seconds:60}") int timeoutSeconds,
            ObjectMapper objectMapper
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[FAL] FAL_KEY no configurada.");
        }
        this.defaultModel = defaultModel;
        this.baseUrl = baseUrl;
        this.modelSafe = modelSafe;
        this.modelSensual = modelSensual;
        this.modelNude = modelNude;
        this.modelExplicit = modelExplicit;
        this.modelNudeFallback = modelNudeFallback;
        this.safetyTolerance = safetyTolerance;
        this.maxRetries = Math.max(1, maxRetries);
        this.refundOnBlocked = refundOnBlocked;
        this.validateOutput = validateOutput;
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
        AdultLevel level = input.adultLevel() != null ? input.adultLevel() : AdultLevel.SENSUAL;

        String selectedModel = resolveModel(level);
        log.info("[FAL] Generating — character={} level={} model={} size={}x{}",
                characterSlug, level, selectedModel, input.width(), input.height());

        Map<String, Object> body = buildRequestBody(input, selectedModel);
        logBody(body);

        JsonNode response = null;
        boolean blocked = false;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            response = callFal(body, selectedModel, characterSlug);
            logResponse(response);

            blocked = isContentBlocked(response);
            if (!blocked) {
                log.info("[FAL] Attempt {} succeeded — character={} model={}", attempt, characterSlug, selectedModel);
                break;
            }

            log.warn("[FAL] Attempt {}/{} blocked (has_nsfw_concepts=true) — character={} model={}",
                    attempt, maxRetries, characterSlug, selectedModel);

            if (attempt == maxRetries) {
                // Si el modelo principal fue NUDE/EXPLICIT, intentar fallback si es diferente
                boolean isNudeLevel = level == AdultLevel.NUDE || level == AdultLevel.EXPLICIT;
                if (isNudeLevel && !modelNudeFallback.equals(selectedModel)) {
                    log.info("[FAL] Trying nude fallback model={} for character={}", modelNudeFallback, characterSlug);
                    Map<String, Object> fallbackBody = buildRequestBody(input, modelNudeFallback);
                    logBody(fallbackBody);
                    response = callFal(fallbackBody, modelNudeFallback, characterSlug);
                    logResponse(response);
                    blocked = isContentBlocked(response);
                    if (!blocked) {
                        log.info("[FAL] Nude fallback model={} succeeded for character={}", modelNudeFallback, characterSlug);
                        selectedModel = modelNudeFallback;
                    }
                }
            }
        }

        // Validar imagen si está habilitado
        if (validateOutput && response != null) {
            String imageUrl = extractUrl(response);
            if (imageUrl != null && isImageTooSmall(imageUrl)) {
                log.warn("[FAL] Output validation: image appears too small (likely black placeholder) — character={} url={}",
                        characterSlug, imageUrl);
                blocked = true;
            }
        }

        if (blocked) {
            log.error("[FAL] Content blocked after all retries — character={} model={} refundOnBlocked={}",
                    characterSlug, selectedModel, refundOnBlocked);
            if (refundOnBlocked) {
                throw new ImageGenerationBlockedException(
                        "El modelo no pudo generar esa imagen. Intenta de nuevo o cambia el nivel.");
            }
        }

        return parseResponse(response, characterSlug, selectedModel);
    }

    // ── Model selection ───────────────────────────────────────────────────────

    private String resolveModel(AdultLevel level) {
        return switch (level) {
            case SAFE     -> modelSafe;
            case SENSUAL  -> modelSensual;
            case NUDE     -> modelNude;
            case EXPLICIT -> modelExplicit;
        };
    }

    // ── HTTP ──────────────────────────────────────────────────────────────────

    private JsonNode callFal(Map<String, Object> body, String model, String characterSlug) {
        String url = baseUrl + "/" + model;
        try {
            return restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException e) {
            handleClientError(e, characterSlug, model);
            return null;
        } catch (HttpServerErrorException e) {
            log.error("[FAL] Server error {} — model={} character={}", e.getStatusCode(), model, characterSlug);
            throw new RuntimeException("El servicio de imágenes no está disponible. Inténtalo más tarde.");
        } catch (Exception e) {
            log.error("[FAL] Error inesperado — model={} character={} cause={}", model, characterSlug, e.getMessage());
            throw new RuntimeException("Error al generar la imagen. Inténtalo de nuevo.");
        }
    }

    // ── Request body ──────────────────────────────────────────────────────────

    private Map<String, Object> buildRequestBody(ImageGenerationInput input, String model) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("prompt", input.positivePrompt());
        body.put("num_images", 1);

        boolean isFlux = model.toLowerCase().contains("flux");
        boolean isSdxl = model.toLowerCase().contains("sdxl");

        if (isFlux) {
            body.put("enable_safety_checker", false);
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
                body.put("safety_tolerance", safetyTolerance);
                body.remove("enable_safety_checker");
            }
        } else {
            // SDXL y otros: width/height directamente
            body.put("width", input.width());
            body.put("height", input.height());
            body.put("enable_safety_checker", false);
            if (!input.negativePrompt().isBlank()) {
                body.put("negative_prompt", input.negativePrompt());
            }
        }

        return body;
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private boolean isContentBlocked(JsonNode response) {
        if (response == null) return false;
        JsonNode nsfw = response.path("has_nsfw_concepts");
        if (nsfw.isArray() && !nsfw.isEmpty()) {
            return nsfw.get(0).asBoolean(false);
        }
        return false;
    }

    /**
     * Validates image is not a tiny black placeholder by checking its size via HEAD request.
     * Real images at 1024x1536 are typically >50KB. Black placeholders are <10KB.
     */
    private boolean isImageTooSmall(String imageUrl) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(imageUrl).openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(5_000);
            conn.connect();
            int contentLength = conn.getContentLength();
            conn.disconnect();
            if (contentLength > 0 && contentLength < 15_000) {
                log.warn("[FAL] Image size={}B is suspiciously small (likely black placeholder) url={}", contentLength, imageUrl);
                return true;
            }
        } catch (Exception e) {
            log.debug("[FAL] Could not HEAD-check image URL: {}", e.getMessage());
        }
        return false;
    }

    // ── Parse ─────────────────────────────────────────────────────────────────

    private ImageGenerationResult parseResponse(JsonNode response, String characterSlug, String model) {
        if (response == null) throw new RuntimeException("fal.ai no devolvió respuesta.");

        JsonNode images = response.path("images");
        if (!images.isArray() || images.isEmpty()) {
            log.error("[FAL] Sin imágenes en respuesta — character={} model={}", characterSlug, model);
            throw new RuntimeException("fal.ai no generó ninguna imagen.");
        }

        String imageUrl = images.get(0).path("url").asText(null);
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new RuntimeException("fal.ai no devolvió URL válida.");
        }

        long seed = response.path("seed").asLong(0L);
        log.info("[FAL] Image generated OK — character={} model={} url={} seed={}",
                characterSlug, model, imageUrl, seed);
        return new ImageGenerationResult(imageUrl, "fal-" + seed);
    }

    private String extractUrl(JsonNode response) {
        if (response == null) return null;
        JsonNode images = response.path("images");
        if (!images.isArray() || images.isEmpty()) return null;
        return images.get(0).path("url").asText(null);
    }

    // ── Error handling ────────────────────────────────────────────────────────

    private void handleClientError(HttpClientErrorException e, String characterSlug, String model) {
        int status = e.getStatusCode().value();
        String body = e.getResponseBodyAsString();
        switch (status) {
            case 401 -> {
                log.error("[FAL] 401 Unauthorized — FAL_KEY inválida. character={} model={}", characterSlug, model);
                throw new RuntimeException("Servicio de imágenes no autorizado.");
            }
            case 422 -> {
                log.error("[FAL] 422 Payload inválido — character={} model={} body={}", characterSlug, model, body);
                throw new RuntimeException("Solicitud inválida al servicio de imágenes.");
            }
            case 429 -> {
                log.warn("[FAL] 429 Rate limit — character={} model={}", characterSlug, model);
                throw new RuntimeException("Límite de generación alcanzado. Intenta en unos minutos.");
            }
            default -> {
                log.error("[FAL] Error {} — character={} model={} body={}", status, characterSlug, model, body);
                throw new RuntimeException("Error al generar la imagen (" + status + ").");
            }
        }
    }

    // ── Logging ───────────────────────────────────────────────────────────────

    private void logBody(Map<String, Object> body) {
        try {
            log.info("[FAL] Request → {}", objectMapper.writeValueAsString(body));
        } catch (Exception ignored) {}
    }

    private void logResponse(JsonNode response) {
        try {
            if (response != null) log.info("[FAL] Response → {}", objectMapper.writeValueAsString(response));
        } catch (Exception ignored) {}
    }

    @Override
    public String providerName() { return "FAL"; }
}
