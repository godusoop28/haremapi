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
 *
 * MODELOS SOPORTADOS (configura con FAL_IMAGE_MODEL):
 *   fal-ai/flux/dev        — Calidad alta, adult content con enable_safety_checker=false (RECOMENDADO)
 *   fal-ai/flux/schnell    — Rápido y barato, 4 pasos, adult content OK
 *   fal-ai/flux-pro/v1.1   — Calidad máxima, safety_tolerance=6
 *   fal-ai/flux-realism    — Estilo fotorrealista
 *
 * NOTA: fal-ai/fast-sdxl tiene filtro NSFW hardcodeado → imagen negra para adult content.
 * NO usar fast-sdxl para contenido adulto.
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
            @Value("${image.fal.model:fal-ai/flux/dev}") String model,
            @Value("${image.fal.base-url:https://fal.run}") String baseUrl,
            @Value("${image.generation-timeout-seconds:60}") int timeoutSeconds
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[FAL] FAL_KEY no configurada — IMAGE_PROVIDER=FAL fallará en tiempo de ejecución.");
        }
        this.model = model;
        this.baseUrl = baseUrl;

        if (model.contains("fast-sdxl") || (model.contains("sdxl") && !model.contains("xl-lightning"))) {
            log.warn("[FAL] ADVERTENCIA: {} tiene filtro NSFW hardcodeado. Para contenido adulto usa fal-ai/flux/dev", model);
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
        log.info("[FAL] Generating image — model={} character={} size={}x{}",
                model, characterSlug, input.width(), input.height());

        Map<String, Object> body = buildRequestBody(input);
        String url = baseUrl + "/" + model;

        log.debug("[FAL] Request body keys={}", body.keySet());

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

    private Map<String, Object> buildRequestBody(ImageGenerationInput input) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("prompt", input.positivePrompt());
        body.put("num_images", 1);

        boolean isFlux = isFluxModel();

        if (isFlux) {
            // FLUX: desactiva el safety checker para permitir contenido adulto
            body.put("enable_safety_checker", false);

            // FLUX acepta imagen como objeto {width, height} para dimensiones exactas
            body.put("image_size", Map.of("width", input.width(), "height", input.height()));

            // Pasos por tipo de modelo
            if (model.contains("schnell")) {
                body.put("num_inference_steps", 4);
            } else if (model.contains("flux/dev") || model.contains("flux-dev")) {
                body.put("num_inference_steps", 28);
            }
            // flux-pro/v1.1: safety_tolerance "6" = más permisivo posible
            if (model.contains("flux-pro") || model.contains("flux/pro")) {
                body.put("safety_tolerance", "6");
                body.remove("enable_safety_checker"); // flux-pro usa safety_tolerance, no enable_safety_checker
            }
        } else {
            // SDXL y otros: width/height directamente
            body.put("width", input.width());
            body.put("height", input.height());
            body.put("enable_safety_checker", false);

            // Para SDXL: negative prompt ayuda a guiar el modelo
            if (!input.negativePrompt().isBlank()) {
                body.put("negative_prompt", input.negativePrompt());
            }
        }

        return body;
    }

    private boolean isFluxModel() {
        return model.contains("flux");
    }

    private ImageGenerationResult handleClientError(HttpClientErrorException e, String characterSlug) {
        int status = e.getStatusCode().value();
        String responseBody = e.getResponseBodyAsString();
        switch (status) {
            case 401 -> {
                log.error("[FAL] Unauthorized — FAL_KEY inválida o expirada. character={}", characterSlug);
                throw new RuntimeException("Servicio de imágenes no autorizado. Contacta al soporte.");
            }
            case 422 -> {
                log.error("[FAL] Payload inválido — character={} body={}", characterSlug, responseBody);
                throw new RuntimeException("No se pudo procesar la solicitud de imagen. Verifica el modelo configurado.");
            }
            case 429 -> {
                log.warn("[FAL] Rate limit alcanzado — character={}", characterSlug);
                throw new RuntimeException("Límite de generación alcanzado. Intenta en unos minutos.");
            }
            default -> {
                log.error("[FAL] Client error {} — character={} body={}", status, characterSlug, responseBody);
                throw new RuntimeException("Error al generar la imagen (" + status + "). Inténtalo de nuevo.");
            }
        }
    }

    private ImageGenerationResult parseResponse(JsonNode response, String characterSlug) {
        if (response == null) {
            log.error("[FAL] Respuesta nula de fal.ai para character={}", characterSlug);
            throw new RuntimeException("El servicio de imágenes no devolvió resultado.");
        }

        JsonNode images = response.path("images");
        if (!images.isArray() || images.isEmpty()) {
            log.error("[FAL] Sin imágenes en respuesta para character={}: {}", characterSlug, response);
            throw new RuntimeException("fal.ai no generó ninguna imagen. Es posible que el modelo haya bloqueado el contenido.");
        }

        String imageUrl = images.get(0).path("url").asText(null);
        if (imageUrl == null || imageUrl.isBlank()) {
            log.error("[FAL] URL vacía en respuesta para character={}", characterSlug);
            throw new RuntimeException("fal.ai no devolvió URL de imagen válida.");
        }

        long seed = response.path("seed").asLong(0L);
        String jobId = "fal-" + seed;

        log.info("[FAL] Imagen generada exitosamente — character={} url={} seed={}", characterSlug, imageUrl, seed);
        return new ImageGenerationResult(imageUrl, jobId);
    }

    @Override
    public String providerName() {
        return "FAL";
    }
}
