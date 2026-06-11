package com.harems.api.admin.dto;

import com.harems.api.subscription.PlanType;
import com.harems.api.user.Role;

import java.time.LocalDateTime;

public record AdminUserResponse(
        Long id,
        String name,
        String email,
        Role role,
        PlanType plan,
        LocalDateTime planExpiresAt,
        Integer imageCredits,
        Integer messagesUsed,
        boolean ageVerified,
        LocalDateTime createdAt
) {
}
