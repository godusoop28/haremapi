package com.harems.api.image;

import com.harems.api.character.Character;
import com.harems.api.image.dto.ImageGenerationRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Builds image generation prompts optimised for FLUX-family models (fal.ai).
 * The character's imagePromptBase provides the visual foundation;
 * style, mood and the user's optional hint enrich it.
 */
@Service
public class ImagePromptBuilder {

    @Value("${image.default-width:768}")
    private int defaultWidth;

    @Value("${image.default-height:1024}")
    private int defaultHeight;

    public ImageGenerationInput build(Character character, ImageGenerationRequest request) {
        String base     = character.getImagePromptBase();
        String style    = resolveStyle(request.style());
        String mood     = resolveMood(request.mood());
        String userHint = buildUserHint(request.userPrompt());

        // FLUX responds best to descriptive, natural-language prompts.
        // Structure: [character visual base], [style], [mood/scene], [user hint], quality tags
        String positivePrompt = String.format(
                "%s, %s, %s atmosphere%s, " +
                "fictional adult character, cinematic portrait, " +
                "professional photography lighting, beautiful, high quality, detailed",
                base, style, mood, userHint
        );

        // Negative prompt kept minimal — FLUX handles content guidance internally.
        // Kept for backward compatibility with SDXL-based providers.
        String negativePrompt =
                "minor, child, underage, real person, celebrity, violence, " +
                "ugly, blurry, deformed, bad anatomy, watermark, low quality";

        int[] dimensions = resolveDimensions(request.aspectRatio());

        return new ImageGenerationInput(
                positivePrompt,
                negativePrompt,
                dimensions[0],
                dimensions[1],
                4,    // steps (fixed for flux/schnell; ignored by fal for flux-pro)
                1.0f, // cfg (not used by FLUX)
                character
        );
    }

    private String resolveStyle(String style) {
        if (style == null || style.isBlank()) {
            return "semi-realistic anime art style";
        }
        return switch (style.toLowerCase()) {
            case "anime"                  -> "anime illustration style";
            case "realistic"              -> "photorealistic photography style";
            case "premium-realistic-anime" -> "semi-realistic anime art style";
            default                       -> "semi-realistic anime art style";
        };
    }

    private String resolveMood(String mood) {
        if (mood == null || mood.isBlank()) return "elegant and confident";
        return switch (mood.toLowerCase()) {
            case "sensual"    -> "sensual and confident";
            case "playful"    -> "playful and joyful";
            case "mysterious" -> "mysterious and intriguing";
            case "romantic"   -> "romantic and warm";
            default           -> "elegant and confident";
        };
    }

    private String buildUserHint(String userPrompt) {
        if (userPrompt == null || userPrompt.isBlank()) return "";
        String sanitized = userPrompt
                .replaceAll("[<>\"'{}|\\\\^`]", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (sanitized.isBlank()) return "";
        int maxLen = Math.min(sanitized.length(), 150);
        return ", " + sanitized.substring(0, maxLen);
    }

    private int[] resolveDimensions(String aspectRatio) {
        if ("landscape".equalsIgnoreCase(aspectRatio)) return new int[]{defaultHeight, defaultWidth};
        if ("square".equalsIgnoreCase(aspectRatio))    return new int[]{768, 768};
        return new int[]{defaultWidth, defaultHeight}; // portrait default
    }
}
