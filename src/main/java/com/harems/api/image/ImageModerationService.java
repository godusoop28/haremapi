package com.harems.api.image;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Checks whether a user-submitted image prompt contains prohibited content.
 * Blocks minors, real persons, celebrities, deepfakes, coercion, and violence.
 */
@Service
public class ImageModerationService {

    private static final List<Pattern> BLOCKED_PATTERNS = List.of(
            // Minors – words
            pattern("\\b(ni[ñn]a|ni[ñn]o|menor|adolescente|teen menor|schoolgirl menor|underage|minor|child|kid|baby|beb[eé]|lolita|loli|shota|preteen|pre-teen)\\b"),
            // Minors – numeric ages under 18
            pattern("\\b(1[0-7])\\s*(a[ñn]os?|years?|yr|yo)\\b"),
            // Coercion and violence
            pattern("\\b(violaci[oó]n|violacion|forzad[ao]|non.?consent|non.?consentida|rape|coerci[oó]n|coercion|abuso sexual|sexual abuse|snuff)\\b"),
            // Unconsciousness / drugs to subdue
            pattern("\\b(inconsciente|unconscious|drogad[ao]|drugged|k\\.?o\\.?|knocked out|sedada|sedated)\\b"),
            // Real persons / celebrities
            pattern("\\b(persona real|real person|famoso[as]?|celebrity|celebridad|actor|actriz|modelo real)\\b"),
            pattern("(deepfake|face.?swap|face.?replace|desnuda[r]? foto|undress photo|exnovia|vecina real)"),
            // Explicit illegal
            pattern("\\b(cp|child porn|kiddie|pedo|pedofilia|child sexual|csam)\\b")
    );

    public boolean isBlocked(String text) {
        if (text == null || text.isBlank()) return false;
        String normalized = text.toLowerCase().trim();
        for (Pattern pattern : BLOCKED_PATTERNS) {
            if (pattern.matcher(normalized).find()) {
                return true;
            }
        }
        return false;
    }

    private static Pattern pattern(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }
}
