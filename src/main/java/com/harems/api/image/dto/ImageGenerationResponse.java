package com.harems.api.image.dto;

public record ImageGenerationResponse(
        Long id,
        String imageUrl,
        String characterSlug,
        String status,
        Integer creditsRemaining,
        Integer creditsCost,
        boolean highTrust,
        int messageCount
) {
}
