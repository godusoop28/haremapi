package com.harems.api.admin.dto;

import java.time.LocalDateTime;

public record AdminImageGenerationResponse(
        Long id,
        String userEmail,
        String characterSlug,
        String userPrompt,
        String promptFinal,
        String imageUrl,
        String status,
        String provider,
        Integer creditsCost,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
}
