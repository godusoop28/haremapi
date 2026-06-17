package com.harems.api.image.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ImageGenerationRequest(
        @NotBlank(message = "El personaje es obligatorio.")
        String characterSlug,

        @Size(max = 200, message = "La descripción no puede superar los 200 caracteres.")
        String userPrompt,

        String style,

        String mood,

        String aspectRatio
) {
}
