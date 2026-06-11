package com.harems.api.image;

import com.harems.api.character.Character;

/**
 * Abstraction over the image generation backend.
 * <p>
 * Today only {@link MockImageGenerationProvider} is wired up. In the future
 * a {@code ComfyUIImageGenerationProvider} (backed by RunPod/ComfyUI/Stable
 * Diffusion) can implement this interface without changing
 * {@link ImageGenerationService}.
 */
public interface ImageGenerationProvider {

    /**
     * @param character the character the image is for
     * @param prompt    the prompt describing the desired image
     * @return the URL of the generated image
     */
    String generateImage(Character character, String prompt);
}
