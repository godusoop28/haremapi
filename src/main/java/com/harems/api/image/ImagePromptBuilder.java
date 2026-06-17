package com.harems.api.image;

import com.harems.api.character.Character;
import com.harems.api.image.dto.ImageGenerationRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Builds the final positive and negative prompts for image generation.
 * The user's optional hint is appended only after passing moderation.
 */
@Service
public class ImagePromptBuilder {

    @Value("${image.default-width:768}")
    private int defaultWidth;

    @Value("${image.default-height:1024}")
    private int defaultHeight;

    @Value("${image.default-steps:25}")
    private int defaultSteps;

    @Value("${image.default-cfg:7.0}")
    private float defaultCfg;

    private static final String NEGATIVE_PROMPT =
            "(minor, child, underage, teenager, childlike, loli, shota, little girl, young girl), " +
            "(real person, celebrity, famous, deepfake, face swap, real human photo, photograph of real woman), " +
            "(violence, non-consent, rape, unconscious, drugged, coercion, forced, snuff), " +
            "(ugly, blurry, deformed, bad anatomy, extra limbs, missing limbs, text, watermark, " +
            "signature, low quality, worst quality, pixelated, sketchy, disfigured, mutation)";

    public ImageGenerationInput build(Character character, ImageGenerationRequest request) {
        String style = resolveStyle(request.style());
        String mood = resolveMood(request.mood());
        String userHint = buildUserHint(request.userPrompt());

        String positivePrompt = String.format(
                "fictional adult woman character, %s, %s, %s atmosphere%s, " +
                "cinematic vertical portrait, professional studio lighting, " +
                "elegant, beautiful face, realistic proportions, 8k high quality, detailed",
                style,
                character.getImagePromptBase(),
                mood,
                userHint
        );

        int[] dimensions = resolveDimensions(request.aspectRatio());

        return new ImageGenerationInput(
                positivePrompt,
                NEGATIVE_PROMPT,
                dimensions[0],
                dimensions[1],
                defaultSteps,
                defaultCfg,
                character
        );
    }

    private String resolveStyle(String style) {
        if (style == null || style.isBlank()) return "premium realistic anime style, semi-realistic illustration";
        return switch (style.toLowerCase()) {
            case "anime" -> "anime style illustration, cel shaded";
            case "realistic" -> "photorealistic, ultra detailed skin, 8k";
            case "premium-realistic-anime" -> "premium realistic anime style, semi-realistic illustration";
            default -> "premium realistic anime style, semi-realistic illustration";
        };
    }

    private String resolveMood(String mood) {
        if (mood == null || mood.isBlank()) return "neutral elegant";
        return switch (mood.toLowerCase()) {
            case "sensual" -> "sensual and confident";
            case "playful" -> "playful and joyful";
            case "mysterious" -> "mysterious and intriguing";
            case "romantic" -> "romantic and warm";
            default -> "neutral elegant";
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
        if ("square".equalsIgnoreCase(aspectRatio)) return new int[]{768, 768};
        return new int[]{defaultWidth, defaultHeight}; // portrait default
    }
}
