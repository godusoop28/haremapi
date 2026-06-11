package com.harems.api.message.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        @NotBlank(message = "El personaje es obligatorio.")
        String characterSlug,

        @NotBlank(message = "El mensaje no puede estar vacío.")
        String message
) {
}
