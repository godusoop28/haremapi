package com.harems.api.image;

import com.harems.api.character.Character;

public record ImageGenerationInput(
        String positivePrompt,
        String negativePrompt,
        int width,
        int height,
        int steps,
        float cfg,
        Character character
) {
}
