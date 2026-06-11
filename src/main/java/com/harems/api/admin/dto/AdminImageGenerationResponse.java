package com.harems.api.admin.dto;

import java.time.LocalDateTime;

public record AdminImageGenerationResponse(
        Long id,
        String userEmail,
        String characterSlug,
        String prompt,
        String imageUrl,
        String status,
        LocalDateTime createdAt
) {
}
