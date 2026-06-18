package com.harems.api.image;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Checks user-submitted image prompts for absolute legal limits only.
 * Adult content, nudity and sexual requests are intentionally allowed.
 */
@Service
public class ImageModerationService {

    private static final List<Pattern> BLOCKED_PATTERNS = List.of(
            // Menores de edad — limite legal absoluto
            pattern("\\b(loli|shota|preteen|pre.?teen|child porn|kiddie porn|csam|"
                    + "pedofilia|pedo(?:filo)?|minor sexual|child sexual|underage sex)\\b"),

            // Edades numericas menores de 18 en contexto sexual explicito
            pattern("\\b(1[0-7])\\s*(a[nn]os?|years?\\s*old)\\b"),

            // No consentimiento / violencia sexual
            pattern("\\b(violacion|rape|coercion|non.?consent|forzad[ao]\\s+sex|sexual abuse)\\b"),

            // Deepfakes de personas reales
            pattern("\\b(deepfake|face.?swap|face.?replace|undress.?real|foto.?real.?desnud)\\b")
    );

    public boolean isBlocked(String text) {
        if (text == null || text.isBlank()) return false;
        String normalized = text.toLowerCase().trim();
        for (Pattern p : BLOCKED_PATTERNS) {
            if (p.matcher(normalized).find()) return true;
        }
        return false;
    }

    private static Pattern pattern(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }
}
