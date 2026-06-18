package com.harems.api.image;

import com.harems.api.character.Character;
import com.harems.api.image.dto.ImageGenerationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Construye prompts para fal.ai (flux-pro/v1.1 con safety_tolerance=6).
 *
 * Niveles:
 *   SAFE     – retrato con ropa elegante
 *   SENSUAL  – outfit sugerente, pose provocadora, sin desnudez
 *   NUDE     – desnudo completo, partes del cuerpo visibles incluyendo pechos y pezones
 *   EXPLICIT – escena adulta explicita con cuerpo completo expuesto
 */
@Slf4j
@Service
public class ImagePromptBuilder {

    @Value("${image.default-width:768}")
    private int defaultWidth;

    @Value("${image.default-height:1024}")
    private int defaultHeight;

    // ── Entry point principal ─────────────────────────────────────────────────

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

        return new ImageGenerationInput(positive, negative, dims[0], dims[1], 4, 1.0f, character);
    }

    /** Backward-compat sin contexto — default NUDE. */
    public ImageGenerationInput build(Character character, ImageGenerationRequest request) {
        return buildWithContext(character, request,
                ImageContextAnalysis.defaults(AdultLevel.NUDE, "confident sensual",
                        "intimate private room with soft warm lighting"));
    }

    // ── Construccion del prompt positivo ─────────────────────────────────────

    private String buildPositive(
            Character character,
            AdultLevel level,
            String mood,
            String scene,
            String poseHint,
            String style,
            String userHint
    ) {
        StringBuilder sb = new StringBuilder();

        // 1. Base fisica del personaje (descripcion detallada con atributos)
        sb.append(character.getImagePromptBase());

        // 2. Contenido adulto segun nivel
        sb.append(", ").append(adultContent(level));

        // 3. Pose
        sb.append(", ").append(poseHint);

        // 4. Escena/ambiente
        sb.append(", ").append(scene);

        // 5. Mood / atmosfera
        sb.append(", ").append(mood).append(" mood");

        // 6. Estilo visual
        sb.append(", ").append(style);

        // 7. Peticion especifica del usuario (si existe)
        if (!userHint.isBlank()) sb.append(", ").append(userHint);

        // 8. Calidad y detalles
        sb.append(", ").append(qualityTags(level));

        // 9. Tags de seguridad (afirmar adulto ficticio)
        sb.append(", ").append(safetyTags());

        return sb.toString();
    }

    /**
     * Contenido adulto por nivel.
     * Para flux-pro con safety_tolerance=6 podemos usar lenguaje directo.
     */
    private String adultContent(AdultLevel level) {
        return switch (level) {
            case SAFE ->
                    "fully clothed in elegant stylish outfit, sophisticated look, confident pose";

            case SENSUAL ->
                    "revealing outfit, very low neckline showing ample cleavage, "
                    + "form-fitting clothing that accentuates curves, "
                    + "alluring seductive pose, bare shoulders and legs visible, "
                    + "lingerie or revealing dress";

            case NUDE ->
                    // Lenguaje directo — flux-pro safety_tolerance=6 lo soporta
                    "completely nude, no clothing, bare naked body fully exposed, "
                    + "bare breasts fully visible, nipples exposed and visible, "
                    + "large voluptuous breasts, full nude figure, "
                    + "bare hips and buttocks visible, all intimate body parts exposed, "
                    + "natural nude adult body, tasteful erotic nude photography";

            case EXPLICIT ->
                    "fully naked body, bare breasts with visible erect nipples, "
                    + "large full breasts exposed completely, wide hips and round bare buttocks, "
                    + "all body parts fully visible and exposed, "
                    + "explicit adult erotic pose, intimate nude adult scene, "
                    + "provocative naked position, adult explicit content";
        };
    }

    private String qualityTags(AdultLevel level) {
        return switch (level) {
            case SAFE, SENSUAL ->
                    "high quality, detailed, beautiful, sharp focus, professional lighting, cinematic";
            case NUDE ->
                    "high quality, masterpiece, detailed skin texture, natural skin, "
                    + "soft intimate lighting, sharp focus, boudoir photography style, "
                    + "anatomically correct, detailed body";
            case EXPLICIT ->
                    "high quality, masterpiece, very detailed, sharp focus, "
                    + "detailed skin texture, vivid, intimate lighting, adult art";
        };
    }

    private String safetyTags() {
        return "fictional adult character, all characters are adults over 18, "
                + "no minors, no real person, no celebrity";
    }

    private String buildNegative(AdultLevel level) {
        // Bloques absolutos siempre
        String always = "minor, child, underage, loli, shota, preteen, "
                + "real person, celebrity, deepfake, "
                + "rape, non-consensual, violence, gore, "
                + "ugly, deformed, blurry, bad anatomy, extra limbs, "
                + "watermark, text, signature, logo";

        return switch (level) {
            case SAFE    -> always + ", revealing, cleavage, nudity, explicit";
            case SENSUAL -> always + ", nudity, explicit nudity";
            case NUDE, EXPLICIT -> always;  // NO bloquear desnudez ni contenido adulto
        };
    }

    // ── Estilo visual ─────────────────────────────────────────────────────────

    private String resolveStyle(String style) {
        if (style == null || style.isBlank()) return "semi-realistic anime art style, premium quality";
        return switch (style.toLowerCase()) {
            case "anime"                   -> "anime illustration style, high quality";
            case "realistic"               -> "photorealistic style, ultra detailed, 8k";
            case "premium-realistic-anime" -> "semi-realistic anime art style, premium quality";
            default                        -> "semi-realistic anime art style, premium quality";
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
