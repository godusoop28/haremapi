package com.harems.api.image.dto;

public record ImageGenerationResponse(
        String imageUrl,
        Integer creditsRemaining,
        String status
) {
}
