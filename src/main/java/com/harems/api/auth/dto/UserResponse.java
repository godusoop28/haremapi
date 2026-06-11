package com.harems.api.auth.dto;

import com.harems.api.subscription.PlanType;
import com.harems.api.user.Role;

public record UserResponse(
        Long id,
        String name,
        String email,
        Role role,
        PlanType plan,
        boolean ageVerified
) {
}
