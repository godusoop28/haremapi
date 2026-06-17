package com.harems.api.image;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "image.provider", havingValue = "MOCK", matchIfMissing = true)
public class MockImageGenerationProvider implements ImageGenerationProvider {

    @Override
    public ImageGenerationResult generate(ImageGenerationInput input) {
        log.info("[MOCK] Generating image for character={} prompt={}",
                input.character().getSlug(),
                input.positivePrompt().substring(0, Math.min(60, input.positivePrompt().length())) + "...");
        return new ImageGenerationResult(input.character().getImageUrl(), "mock-job-" + System.currentTimeMillis());
    }

    @Override
    public String providerName() {
        return "MOCK";
    }
}
