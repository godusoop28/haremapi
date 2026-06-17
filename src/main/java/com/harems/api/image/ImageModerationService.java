package com.harems.api.image;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Checks user-submitted image prompts for prohibited content.
 *
 * BLOCKED (always):
 *   – Minors, underage, loli/shota, preteen
 *   – Real persons, celebrities, deepfakes, face-swap tools
 *   – Non-consent, rape, coercion, forced acts, drugging, unconscious
 *   – Child sexual content / illegal content
 *
 * ALLOWED (intentionally NOT blocked):
 *   – Nude, naked, desnuda, desnudo — adult nudity of fictional characters
 *   – Sensual, erotic, intimate, explicit — adult fictional content
 *   – Adult, mature — adult platform content
 */
@Service
public class ImageModerationService {

    private static final List<Pattern> BLOCKED_PATTERNS = List.of(

            // ── Minors ──────────────────────────────────────────────────────
            pattern("\\b(ni[ñn]a|ni[ñn]o|menor de edad|adolescente menor|underage|minor|child|kid|"
                    + "beb[eé]|lolita|loli|shota|preteen|pre-teen|schoolgirl menor)\\b"),

            // Numeric ages under 18 in context like "17 años", "16 years"
            pattern("\\b(1[0-7])\\s*(a[ñn]os?|years?\\s*old|yr\\s*old|yo\\b)"),

            // ── Non-consent / coercion / violence ───────────────────────────
            pattern("\\b(violaci[oó]n|violacion|forzad[ao]|non.?consent|rape|"
                    + "coerci[oó]n|coercion|abuso sexual|sexual abuse|snuff|"
                    + "a la fuerza|sin su consentimiento)\\b"),

            // Unconsciousness / drugged to subdue
            pattern("\\b(inconsciente|unconscious|drogad[ao]|drugged|"
                    + "k\\.?o\\.?|knocked out|sedada|sedated)\\b"),

            // ── Real persons / celebrities / deepfakes ───────────────────────
            // Require "real" qualifier for actor/actress/model to avoid blocking fictional archetypes
            pattern("\\b(persona real|real person|celebrity|celebridad|famoso[as]?)\\b"),
            pattern("\\b(actor|actriz|modelo)\\s+(real|famoso|conocido|de hollywood|de cine real)\\b"),
            pattern("(deepfake|face.?swap|face.?replace|undress.?photo|"
                    + "desnudar.?foto real|exnovia real|vecina real|foto real)"),

            // ── Child sexual content / illegal ───────────────────────────────
            pattern("\\b(cp\\b|child porn|kiddie porn|pedo(?:filia)?|child sexual|csam)\\b")
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
