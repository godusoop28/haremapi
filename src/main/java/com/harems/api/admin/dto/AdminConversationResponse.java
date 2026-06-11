package com.harems.api.admin.dto;

import java.time.LocalDateTime;

public record AdminConversationResponse(
        Long id,
        String userEmail,
        String characterSlug,
        String characterName,
        long messageCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
