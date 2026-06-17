package com.harems.api.image;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * RunPod Serverless image provider.
 * Sends a job to the RunPod endpoint and polls until COMPLETED or FAILED.
 * Activate with: IMAGE_PROVIDER=RUNPOD
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "image.provider", havingValue = "RUNPOD")
public class RunPodImageProvider implements ImageGenerationProvider {

    private final RestClient restClient;
    private final String apiKey;
    private final String endpointId;
    private final String baseUrl;
    private final int timeoutSeconds;

    public RunPodImageProvider(
            @Value("${image.runpod.api-key:}") String apiKey,
            @Value("${image.runpod.endpoint-id:}") String endpointId,
            @Value("${image.runpod.base-url:https://api.runpod.ai/v2}") String baseUrl,
            @Value("${image.generation-timeout-seconds:120}") int timeoutSeconds
    ) {
        this.apiKey = apiKey;
        this.endpointId = endpointId;
        this.baseUrl = baseUrl;
        this.timeoutSeconds = timeoutSeconds;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public ImageGenerationResult generate(ImageGenerationInput input) {
        log.info("Calling RunPod endpoint={} for character={}", endpointId, input.character().getSlug());

        Map<String, Object> jobInput = Map.of(
                "prompt", input.positivePrompt(),
                "negative_prompt", input.negativePrompt(),
                "width", input.width(),
                "height", input.height(),
                "num_inference_steps", input.steps(),
                "guidance_scale", input.cfg(),
                "sampler_name", "DPM++ 2M Karras"
        );
        Map<String, Object> body = Map.of("input", jobInput);

        RunPodJobResponse submitResponse = restClient.post()
                .uri("/{endpointId}/run", endpointId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(RunPodJobResponse.class);

        if (submitResponse == null || submitResponse.id() == null) {
            throw new RuntimeException("RunPod did not return a job ID");
        }

        String jobId = submitResponse.id();
        log.info("RunPod job submitted jobId={}", jobId);

        return pollUntilComplete(jobId);
    }

    private ImageGenerationResult pollUntilComplete(String jobId) {
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);
        int pollIntervalMs = 3000;

        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Image generation interrupted");
            }

            RunPodJobResponse status = restClient.get()
                    .uri("/{endpointId}/status/{jobId}", endpointId, jobId)
                    .retrieve()
                    .body(RunPodJobResponse.class);

            if (status == null) continue;

            log.info("RunPod poll jobId={} status={}", jobId, status.status());

            if ("COMPLETED".equals(status.status())) {
                String imageUrl = extractImageUrl(status);
                log.info("RunPod job completed jobId={}", jobId);
                return new ImageGenerationResult(imageUrl, jobId);
            }

            if ("FAILED".equals(status.status()) || "CANCELLED".equals(status.status())) {
                log.error("RunPod job failed jobId={} status={}", jobId, status.status());
                throw new RuntimeException("RunPod job " + status.status() + " jobId=" + jobId);
            }
        }

        throw new RuntimeException("RunPod job timed out after " + timeoutSeconds + "s jobId=" + jobId);
    }

    private String extractImageUrl(RunPodJobResponse response) {
        if (response.output() == null || response.output().images() == null || response.output().images().isEmpty()) {
            throw new RuntimeException("RunPod returned empty output for jobId=" + response.id());
        }
        String raw = response.output().images().get(0);
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            return raw;
        }
        // base64 image → data URI (browser can display this)
        if (!raw.startsWith("data:")) {
            return "data:image/png;base64," + raw;
        }
        return raw;
    }

    @Override
    public String providerName() {
        return "RUNPOD";
    }

    // Internal DTO records for RunPod API

    record RunPodJobResponse(String id, String status, RunPodOutput output) {}

    record RunPodOutput(List<String> images) {}
}
