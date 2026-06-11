package com.harems.api.conversation.dto;

import com.harems.api.message.dto.MessageResponse;

import java.time.LocalDateTime;
import java.util.List;

public record ConversationResponse(
        Long id,
        String characterSlug,
        String characterName,
        String characterImageUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<MessageResponse> messages
) {
}
