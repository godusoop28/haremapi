package com.harems.api.image;

import tools.jackson.databind.JsonNode;
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
 * Supports fal-ai/flux/schnell (fast, default) and fal-ai/flux-pro/v1.1 (quality).
 * Activate with: IMAGE_PROVIDER=FAL
 * Required env: FAL_KEY
 * Optional env: FAL_IMAGE_MODEL, FAL_BASE_URL, IMAGE_GENERATION_TIMEOUT_SECONDS
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "image.provider", havingValue = "FAL")
public class FalImageProvider implements ImageGenerationProvider {

    private final RestClient restClient;
    private final String model;
    private final String baseUrl;

    public FalImageProvider(
            @Value("${image.fal.api-key:}") String apiKey,
            @Value("${image.fal.model:fal-ai/flux/schnell}") String model,
            @Value("${image.fal.base-url:https://fal.run}") String baseUrl,
            @Value("${image.generation-timeout-seconds:60}") int timeoutSeconds
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[FAL] FAL_KEY no configurada — IMAGE_PROVIDER=FAL fallará en tiempo de ejecución.");
        }
        this.model = model;
        this.baseUrl = baseUrl;

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
        log.info("[FAL] Generating image — model={} character={}", model, characterSlug);

        Map<String, Object> body = buildRequestBody(input);
        String url = baseUrl + "/" + model;

        try {
            JsonNode response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            return parseResponse(response, characterSlug);

        } catch (HttpClientErrorException e) {
            return handleClientError(e, characterSlug);
        } catch (HttpServerErrorException e) {
            log.error("[FAL] Server error — status={} character={} body={}",
                    e.getStatusCode(), characterSlug, e.getResponseBodyAsString());
            throw new RuntimeException("El servicio de imágenes no está disponible. Inténtalo más tarde.");
        } catch (Exception e) {
            log.error("[FAL] Error inesperado — character={} cause={}", characterSlug, e.getMessage());
            throw new RuntimeException("Error al generar la imagen. Inténtalo de nuevo.");
        }
    }

    private ImageGenerationResult handleClientError(HttpClientErrorException e, String characterSlug) {
        int status = e.getStatusCode().value();
        String body = e.getResponseBodyAsString();
        switch (status) {
            case 401 -> {
                log.error("[FAL] Unauthorized — FAL_KEY inválida o expirada. character={}", characterSlug);
                throw new RuntimeException("Servicio de imágenes no autorizado. Contacta al soporte.");
            }
            case 422 -> {
                log.error("[FAL] Payload inválido — character={} body={}", characterSlug, body);
                throw new RuntimeException("No se pudo procesar la solicitud de imagen.");
            }
            case 429 -> {
                log.warn("[FAL] Rate limit alcanzado — character={}", characterSlug);
                throw new RuntimeException("Límite de generación alcanzado. Intenta en unos minutos.");
            }
            default -> {
                log.error("[FAL] Client error {} — character={} body={}", status, characterSlug, body);
                throw new RuntimeException("Error al generar la imagen. Inténtalo de nuevo.");
            }
        }
    }

    private Map<String, Object> buildRequestBody(ImageGenerationInput input) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("prompt", input.positivePrompt());
        body.put("num_images", 1);
        body.put("enable_safety_checker", false);
        body.put("image_size", resolveImageSize(input.width(), input.height()));

        if (model.contains("schnell")) {
            body.put("num_inference_steps", 4);
        }
        if (model.contains("flux-pro") || model.contains("flux/pro")) {
            body.put("safety_tolerance", "5");
        }

        return body;
    }

    private String resolveImageSize(int width, int height) {
        if (width == height) return "square";
        if (width > height) return "landscape_4_3";
        return "portrait_4_3";
    }

    private ImageGenerationResult parseResponse(JsonNode response, String characterSlug) {
        if (response == null) {
            log.error("[FAL] Respuesta nula de fal.ai para character={}", characterSlug);
            throw new RuntimeException("El servicio de imágenes no devolvió resultado.");
        }

        JsonNode images = response.path("images");
        if (!images.isArray() || images.isEmpty()) {
            log.error("[FAL] Sin imágenes en respuesta para character={}: {}", characterSlug, response);
            throw new RuntimeException("fal.ai no generó ninguna imagen.");
        }

        String imageUrl = images.get(0).path("url").asText(null);
        if (imageUrl == null || imageUrl.isBlank()) {
            log.error("[FAL] URL vacía en respuesta para character={}", characterSlug);
            throw new RuntimeException("fal.ai no devolvió URL de imagen válida.");
        }

        long seed = response.path("seed").asLong(0L);
        String jobId = "fal-" + seed;

        log.info("[FAL] Imagen generada — character={} url={} seed={}", characterSlug, imageUrl, seed);
        return new ImageGenerationResult(imageUrl, jobId);
    }

    @Override
    public String providerName() {
        return "FAL";
    }
}
