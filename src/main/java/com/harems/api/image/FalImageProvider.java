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
 * fal.ai image provider.
 *
 * MODELOS RECOMENDADOS (FAL_IMAGE_MODEL):
 *   fal-ai/flux/dev        → adult content OK con enable_safety_checker=false
 *   fal-ai/flux/schnell    → rápido y barato
 *   fal-ai/flux-pro/v1.1   → calidad máxima, safety_tolerance=6
 *
 * NO USAR para adult content:
 *   fal-ai/fast-sdxl       → filtro NSFW hardcodeado, imagen negra inevitable
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

        if (model.contains("fast-sdxl")) {
            log.warn("[FAL] ADVERTENCIA: fast-sdxl tiene filtro NSFW hardcodeado. Cambia a fal-ai/flux/dev.");
        }

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

        Map<String, Object> body = buildRequestBody(input);

        // Log el JSON exacto que vamos a enviar (diagnóstico)
        try {
            String bodyJson = objectMapper.writeValueAsString(body);
            log.info("[FAL] Request JSON → {}", bodyJson);
        } catch (Exception ignored) {}

        String url = baseUrl + "/" + model;

        try {
            JsonNode response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            // Log la respuesta completa para diagnóstico
            try {
                log.info("[FAL] Response JSON → {}", objectMapper.writeValueAsString(response));
            } catch (Exception ignored) {}

            return parseResponse(response, characterSlug);

        } catch (HttpClientErrorException e) {
            log.error("[FAL] Client error {} — body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return handleClientError(e, characterSlug);
        } catch (HttpServerErrorException e) {
            log.error("[FAL] Server error {} — body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("El servicio de imágenes no está disponible. Inténtalo más tarde.");
        } catch (Exception e) {
            log.error("[FAL] Error inesperado — character={} cause={}", characterSlug, e.getMessage(), e);
            throw new RuntimeException("Error al generar la imagen. Inténtalo de nuevo.");
        }
    }

    private Map<String, Object> buildRequestBody(ImageGenerationInput input) {
        Map<String, Object> body = new LinkedHashMap<>();

        body.put("prompt", input.positivePrompt());
        body.put("num_images", 1);
        body.put("enable_safety_checker", false);  // siempre false — desactiva el checker de salida

        boolean isFlux = model.toLowerCase().contains("flux");

        if (isFlux) {
            // FLUX: image_size como objeto para dimensiones exactas
            LinkedHashMap<String, Integer> size = new LinkedHashMap<>();
            size.put("width", input.width());
            size.put("height", input.height());
            body.put("image_size", size);

            if (model.contains("schnell")) {
                body.put("num_inference_steps", 4);
            } else if (model.contains("/dev")) {
                body.put("num_inference_steps", 28);
            }

            // flux-pro: safety_tolerance al máximo
            if (model.contains("flux-pro") || model.contains("flux/pro")) {
                body.put("safety_tolerance", "6");
                body.remove("enable_safety_checker");
            }
        } else {
            // SDXL y otros: width/height directamente + negative prompt
            body.put("width", input.width());
            body.put("height", input.height());
            if (!input.negativePrompt().isBlank()) {
                body.put("negative_prompt", input.negativePrompt());
            }
        }

        return body;
    }

    private ImageGenerationResult parseResponse(JsonNode response, String characterSlug) {
        if (response == null) {
            throw new RuntimeException("fal.ai no devolvió respuesta.");
        }

        // Diagnóstico: verificar si el safety checker bloqueó el contenido
        JsonNode nsfw = response.path("has_nsfw_concepts");
        if (nsfw.isArray() && !nsfw.isEmpty()) {
            boolean flagged = nsfw.get(0).asBoolean(false);
            if (flagged) {
                log.warn("[FAL] ALERTA: has_nsfw_concepts=true para character={}. "
                        + "El safety checker está activo a pesar de enable_safety_checker=false. "
                        + "La imagen probablemente saldrá negra. Verifica el modelo y la cuenta en fal.ai.",
                        characterSlug);
            } else {
                log.info("[FAL] has_nsfw_concepts=false — imagen no bloqueada para character={}", characterSlug);
            }
        } else {
            log.info("[FAL] Campo has_nsfw_concepts no presente en respuesta — el checker no corrió (correcto).");
        }

        JsonNode images = response.path("images");
        if (!images.isArray() || images.isEmpty()) {
            log.error("[FAL] Sin imágenes en respuesta para character={}", characterSlug);
            throw new RuntimeException("fal.ai no generó ninguna imagen.");
        }

        String imageUrl = images.get(0).path("url").asText(null);
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new RuntimeException("fal.ai no devolvió URL de imagen válida.");
        }

        long seed = response.path("seed").asLong(0L);
        log.info("[FAL] Imagen generada — character={} url={} seed={}", characterSlug, imageUrl, seed);
        return new ImageGenerationResult(imageUrl, "fal-" + seed);
    }

    private ImageGenerationResult handleClientError(HttpClientErrorException e, String characterSlug) {
        int status = e.getStatusCode().value();
        String responseBody = e.getResponseBodyAsString();
        switch (status) {
            case 401 -> {
                log.error("[FAL] 401 Unauthorized — FAL_KEY inválida. character={}", characterSlug);
                throw new RuntimeException("Servicio de imágenes no autorizado.");
            }
            case 422 -> {
                log.error("[FAL] 422 Payload inválido — character={} body={}", characterSlug, responseBody);
                throw new RuntimeException("Solicitud inválida al servicio de imágenes. Verifica el modelo.");
            }
            case 429 -> {
                log.warn("[FAL] 429 Rate limit — character={}", characterSlug);
                throw new RuntimeException("Límite de generación alcanzado. Intenta en unos minutos.");
            }
            default -> {
                log.error("[FAL] Error {} — character={} body={}", status, characterSlug, responseBody);
                throw new RuntimeException("Error al generar la imagen (" + status + ").");
            }
        }
    }

    @Override
    public String providerName() {
        return "FAL";
    }
}
