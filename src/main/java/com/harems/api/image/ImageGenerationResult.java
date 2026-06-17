package com.harems.api.image;

public record ImageGenerationResult(
        String imageUrl,
        String providerJobId
) {
}
