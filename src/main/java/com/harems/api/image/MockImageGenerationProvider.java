package com.harems.api.image;

import com.harems.api.character.Character;
import org.springframework.stereotype.Component;

/**
 * Temporary provider that does not generate any real image.
 * It simply returns the character's base portrait so the frontend has
 * something to display while the real provider (ComfyUI/RunPod) is built.
 */
@Component
public class MockImageGenerationProvider implements ImageGenerationProvider {

    @Override
    public String generateImage(Character character, String prompt) {
        return character.getImageUrl();
    }
}
