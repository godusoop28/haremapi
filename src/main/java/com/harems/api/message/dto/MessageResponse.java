package com.harems.api.message.dto;

import com.harems.api.message.SenderType;

import java.time.LocalDateTime;

public record MessageResponse(
        Long id,
        SenderType sender,
        String content,
        LocalDateTime createdAt
) {
}
