package com.harems.api.auth.dto;

public record AuthResponse(
        String token,
        UserResponse user
) {
}
