package com.harems.api.message.dto;

public record ChatResponse(
        Long conversationId,
        String reply,
        Integer messagesUsed,
        Integer messagesLimit
) {
}
