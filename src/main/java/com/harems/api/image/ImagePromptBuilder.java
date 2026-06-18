package com.harems.api.image;

import com.harems.api.character.Character;
import com.harems.api.image.dto.ImageGenerationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ImagePromptBuilder {

    @Value("${image.default-width:768}")
    private int defaultWidth;

    @Value("${image.default-height:1024}")
    private int defaultHeight;

    public ImageGenerationInput buildWithContext(
            Character character,
            ImageGenerationRequest request,
            ImageContextAnalysis context
    ) {
        AdultLevel level  = context.adultLevel();
        String mood       = context.mood();
        String scene      = overrideIfSet(request.scene(), context.scene());
        String poseHint   = overrideIfSet(request.pose(), context.poseIntent());
        String style      = resolveStyle(request.style());
        String userHint   = sanitize(request.userPrompt());

        String positive = buildPositive(character, level, mood, scene, poseHint, style, userHint);
        String negative = buildNegative(level);

        int[] dims = resolveDimensions(request.aspectRatio());

        log.info("[Prompt] char={} level={} mood={} scene='{}'",
                character.getSlug(), level, mood, truncate(scene, 60));
        log.info("[Prompt] positive='{}'", truncate(positive, 200));

        return new ImageGenerationInput(positive, negative, dims[0], dims[1], 4, 1.0f, character, level);
    }

    /** Backward-compat sin contexto. */
    public ImageGenerationInput build(Character character, ImageGenerationRequest request) {
        return buildWithContext(character, request,
                ImageContextAnalysis.defaults(AdultLevel.NUDE, "confident sensual",
                        "intimate private room with soft warm lighting"));
    }

    // ── Construccion ──────────────────────────────────────────────────────────

    private String buildPositive(
            Character character, AdultLevel level,
            String mood, String scene, String poseHint, String style, String userHint
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append(character.getImagePromptBase());
        sb.append(", ").append(adultContent(level));
        sb.append(", ").append(poseHint);
        sb.append(", ").append(scene);
        sb.append(", ").append(mood).append(" mood");
        sb.append(", ").append(style);
        if (!userHint.isBlank()) sb.append(", ").append(userHint);
        sb.append(", ").append(qualityTags(level));
        sb.append(", ").append(safetyTags());
        return sb.toString();
    }

    private String adultContent(AdultLevel level) {
        return switch (level) {
            case SAFE ->
                    "fully clothed elegant stylish outfit, sophisticated look, confident pose";
            case SENSUAL ->
                    "revealing outfit, very low neckline showing ample cleavage, "
                    + "form-fitting clothing accentuating curves, alluring seductive pose, "
                    + "bare shoulders and legs visible, lingerie or revealing dress, sensual body language";
            case NUDE ->
                    "completely nude body, unclothed, bare skin, "
                    + "bare breasts fully exposed, large full breasts bare, "
                    + "bare hips and buttocks, all body fully bare and visible, "
                    + "boudoir art photography, intimate nude portrait, "
                    + "tasteful adult nude, natural nude feminine body";
            case EXPLICIT ->
                    "fully nude body completely bare, bare chest and breasts exposed, "
                    + "large full bare breasts, wide hips and bare round buttocks, "
                    + "all body parts bare and visible, passionate intimate adult pose, "
                    + "boudoir intimate nude photography, adult intimate art, sensual nude scene";
        };
    }

    private String qualityTags(AdultLevel level) {
        return switch (level) {
            case SAFE, SENSUAL ->
                    "high quality, detailed, beautiful, sharp focus, professional lighting, cinematic";
            case NUDE ->
                    "high quality, masterpiece, detailed skin texture, natural skin, "
                    + "soft intimate lighting, sharp focus, boudoir photography style, anatomically correct";
            case EXPLICIT ->
                    "high quality, masterpiece, very detailed, sharp focus, "
                    + "detailed skin texture, vivid, intimate lighting";
        };
    }

    private String safetyTags() {
        return "fictional adult character, all characters are adults over 18, no minors, no real person, no celebrity";
    }

    private String buildNegative(AdultLevel level) {
        String always = "minor, child, underage, loli, shota, preteen, "
                + "real person, celebrity, deepfake, "
                + "rape, non-consensual, violence, gore, "
                + "ugly, deformed, blurry, bad anatomy, watermark, text, logo";
        return switch (level) {
            case SAFE    -> always + ", revealing, cleavage, nudity, explicit";
            case SENSUAL -> always + ", nudity, explicit nudity";
            case NUDE, EXPLICIT -> always;
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String resolveStyle(String style) {
        if (style == null || style.isBlank()) return "semi-realistic anime art style, premium quality";
        return switch (style.toLowerCase()) {
            case "anime"                   -> "anime illustration style, high quality";
            case "realistic"               -> "photorealistic style, ultra detailed";
            case "premium-realistic-anime" -> "semi-realistic anime art style, premium quality";
            default                        -> "semi-realistic anime art style, premium quality";
        };
    }

    private String sanitize(String userPrompt) {
        if (userPrompt == null || userPrompt.isBlank()) return "";
        return userPrompt.replaceAll("[<>\"'{}|\\\\^`]", "")
                .replaceAll("\\s+", " ").trim()
                .substring(0, Math.min(userPrompt.length(), 200));
    }

    private String overrideIfSet(String explicit, String fallback) {
        return (explicit != null && !explicit.isBlank()) ? explicit.trim() : fallback;
    }

    private int[] resolveDimensions(String aspectRatio) {
        if ("landscape".equalsIgnoreCase(aspectRatio)) return new int[]{defaultHeight, defaultWidth};
        if ("square".equalsIgnoreCase(aspectRatio))    return new int[]{768, 768};
        return new int[]{defaultWidth, defaultHeight};
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
