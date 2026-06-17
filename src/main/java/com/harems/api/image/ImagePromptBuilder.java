package com.harems.api.image;

import com.harems.api.character.Character;
import com.harems.api.image.dto.ImageGenerationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Builds image generation prompts for fal.ai (FLUX family).
 *
 * Supports four adult levels:
 *   SAFE     – fully clothed, portrait/editorial
 *   SENSUAL  – revealing but no nudity, alluring pose (default)
 *   NUDE     – adult tasteful nudity, fictional adult character
 *   EXPLICIT – direct adult scene, fictional consensual adult content
 *
 * The {@link #buildWithContext} entry-point uses the analyzed conversation
 * context to produce a prompt that feels like a visual continuation of the chat.
 */
@Slf4j
@Service
public class ImagePromptBuilder {

    @Value("${image.default-width:768}")
    private int defaultWidth;

    @Value("${image.default-height:1024}")
    private int defaultHeight;

    // ── Main entry-point ──────────────────────────────────────────────────────

    /**
     * Build a contextual image prompt using conversation analysis.
     */
    public ImageGenerationInput buildWithContext(
            Character character,
            ImageGenerationRequest request,
            ImageContextAnalysis context
    ) {
        AdultLevel level = context.adultLevel();
        String mood      = context.mood();
        String scene     = overrideScene(request.scene(), context.scene());
        String poseHint  = override(request.pose(), context.poseIntent());
        String style     = resolveStyle(request.style());
        String userHint  = sanitizeUserHint(request.userPrompt());

        String positivePrompt = buildPositivePrompt(character, level, mood, scene, poseHint, style, userHint);
        String negativePrompt = buildNegativePrompt(level);

        int[] dims = resolveDimensions(request.aspectRatio());

        log.info("[Prompt] char={} level={} mood={} scene='{}' style={} hint='{}'",
                character.getSlug(), level, mood, truncate(scene, 60), style, truncate(userHint, 40));
        log.debug("[Prompt] positive='{}'", truncate(positivePrompt, 120));

        return new ImageGenerationInput(positivePrompt, negativePrompt, dims[0], dims[1], 4, 1.0f, character);
    }

    /** Backward-compat: build without context (uses SENSUAL default). */
    public ImageGenerationInput build(Character character, ImageGenerationRequest request) {
        ImageContextAnalysis defaults = ImageContextAnalysis.defaults(
                AdultLevel.SENSUAL, "confident sensual", "intimate private room with soft lighting");
        return buildWithContext(character, request, defaults);
    }

    // ── Prompt construction ───────────────────────────────────────────────────

    private String buildPositivePrompt(
            Character character,
            AdultLevel level,
            String mood,
            String scene,
            String poseHint,
            String style,
            String userHint
    ) {
        String base     = character.getImagePromptBase();
        String adultTag = adultContentTag(level, character, mood, userHint);
        String quality  = qualityTags(level);
        String safety   = safetyTags(level);

        // Structure: [character base] + [adult content / pose] + [scene] + [mood] + [style] + [quality] + [safety]
        StringBuilder sb = new StringBuilder();
        sb.append(base);
        sb.append(", ").append(adultTag);
        sb.append(", ").append(scene);
        sb.append(", ").append(mood).append(" atmosphere");
        sb.append(", ").append(style);
        if (!userHint.isBlank()) sb.append(", ").append(userHint);
        sb.append(", ").append(quality);
        sb.append(", ").append(safety);

        return sb.toString();
    }

    private String adultContentTag(AdultLevel level, Character character, String mood, String userHint) {
        return switch (level) {
            case SAFE -> "fully clothed, elegant stylish outfit, confident natural pose";

            case SENSUAL -> "revealing alluring outfit, suggestive sensual pose, "
                    + "partially exposed, inviting expression, intimate body language";

            case NUDE -> {
                // Emphasize it's a fictional adult, tasteful nude
                String base = "nude, no clothing, completely bare, tasteful adult nudity, "
                        + "natural confident nude pose, visible bare body, "
                        + "anatomically natural and proportional, adult fine art nude";
                // Add userHint specifics if they describe the nude scene
                yield base;
            }

            case EXPLICIT -> {
                String base = "explicit adult scene, no clothing, nude, "
                        + "direct adult sexual pose, fictional adult erotica, "
                        + "consensual adult content, provocative explicit pose";
                yield base;
            }
        };
    }

    private String qualityTags(AdultLevel level) {
        String base = "high quality, detailed, beautiful, professional lighting, sharp focus";
        return switch (level) {
            case SAFE, SENSUAL -> base + ", cinematic portrait";
            case NUDE          -> base + ", premium adult art, soft intimate lighting, skin texture detail";
            case EXPLICIT      -> base + ", premium adult art, vivid, skin detail";
        };
    }

    private String safetyTags(AdultLevel level) {
        return switch (level) {
            case SAFE, SENSUAL ->
                    "fictional adult character, over 18, no minors, no real person, no celebrity";
            case NUDE, EXPLICIT ->
                    "fictional adult character, over 18, all characters are adults, "
                            + "no minors, no real person, no celebrity, no deepfake, consensual";
        };
    }

    /**
     * Negative prompt — for SAFE/SENSUAL we block nudity to keep them clean.
     * For NUDE/EXPLICIT we do NOT block nudity (that's the intent).
     * For FLUX models, this is largely ignored but kept for non-FLUX compatibility.
     */
    private String buildNegativePrompt(AdultLevel level) {
        // Always block
        String hardBlocks = "minor, child, underage, loli, shota, preteen, teen under 18, "
                + "real person, celebrity, famous person, deepfake, face swap, "
                + "rape, non-consensual, coercion, forced, violence, gore, "
                + "ugly, deformed, blurry, bad anatomy, extra limbs, missing limbs, "
                + "watermark, text, logo, signature";

        return switch (level) {
            case SAFE     -> hardBlocks + ", nude, naked, explicit, nsfw";
            case SENSUAL  -> hardBlocks + ", fully nude, explicit nudity, nsfw";
            case NUDE, EXPLICIT -> hardBlocks;
        };
    }

    // ── Style resolution ──────────────────────────────────────────────────────

    private String resolveStyle(String style) {
        if (style == null || style.isBlank()) return "semi-realistic anime art style";
        return switch (style.toLowerCase()) {
            case "anime"                   -> "anime illustration style";
            case "realistic"               -> "photorealistic style, ultra detailed";
            case "premium-realistic-anime" -> "semi-realistic anime art style, premium quality";
            default                        -> "semi-realistic anime art style";
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String sanitizeUserHint(String userPrompt) {
        if (userPrompt == null || userPrompt.isBlank()) return "";
        String cleaned = userPrompt
                .replaceAll("[<>\"'{}|\\\\^`]", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.isBlank()) return "";
        int max = Math.min(cleaned.length(), 180);
        return cleaned.substring(0, max);
    }

    private String overrideScene(String fromRequest, String fromContext) {
        if (fromRequest != null && !fromRequest.isBlank()) return fromRequest.trim();
        return fromContext;
    }

    private String override(String explicit, String fallback) {
        return (explicit != null && !explicit.isBlank()) ? explicit.trim() : fallback;
    }

    private int[] resolveDimensions(String aspectRatio) {
        if ("landscape".equalsIgnoreCase(aspectRatio)) return new int[]{defaultHeight, defaultWidth};
        if ("square".equalsIgnoreCase(aspectRatio))    return new int[]{768, 768};
        return new int[]{defaultWidth, defaultHeight}; // portrait default
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
