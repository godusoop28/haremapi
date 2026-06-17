package com.harems.api.image;

/**
 * Abstraction over the image generation backend.
 * Implementations: {@link MockImageGenerationProvider} (default) and
 * {@link RunPodImageProvider} (activated via IMAGE_PROVIDER=RUNPOD).
 */
public interface ImageGenerationProvider {

    ImageGenerationResult generate(ImageGenerationInput input);

    String providerName();
}
