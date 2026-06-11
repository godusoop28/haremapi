package com.harems.api.image.dto;

import jakarta.validation.constraints.NotBlank;

public record ImageGenerationRequest(
        @NotBlank(message = "El personaje es obligatorio.")
        String characterSlug,

        @NotBlank(message = "El tipo de imagen es obligatorio.")
        String type
) {
}
