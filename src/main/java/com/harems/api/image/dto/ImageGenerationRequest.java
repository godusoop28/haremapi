package com.harems.api.image.dto;

import com.harems.api.image.AdultLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ImageGenerationRequest(
        @NotBlank(message = "El personaje es obligatorio.")
        String characterSlug,

        @Size(max = 300, message = "La descripción no puede superar los 300 caracteres.")
        String userPrompt,

        String style,

        String mood,

        String aspectRatio,

        /** Optional explicit adult level. If null, auto-detected from userPrompt + context. */
        AdultLevel adultLevel,

        /** Optional scene override (e.g. "on the beach"). */
        String scene,

        /** Optional pose override. */
        String pose
) {
}
