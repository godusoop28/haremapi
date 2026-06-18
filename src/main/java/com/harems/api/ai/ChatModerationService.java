package com.harems.api.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Moderación de mensajes de chat.
 *
 * SOLO BLOQUEA contenido verdaderamente ilegal o dañino.
 * NO bloquea lenguaje vulgar, sexual o adulto consensuado entre adultos ficticios.
 *
 * Palabras como "tetas", "culo", "sexo", "desnuda", etc. NO están bloqueadas.
 */
@Slf4j
@Service
public class ChatModerationService {

    private final boolean adultModeEnabled;
    private final boolean allowVulgar;
    private final boolean explicitRoleplayEnabled;
    private final int explicitMinConnection;

    public ChatModerationService(
            @Value("${chat.adult-mode-enabled:true}") boolean adultModeEnabled,
            @Value("${chat.allow-vulgar-language:true}") boolean allowVulgar,
            @Value("${chat.explicit-roleplay-enabled:true}") boolean explicitRoleplayEnabled,
            @Value("${chat.explicit-min-connection:0}") int explicitMinConnection
    ) {
        this.adultModeEnabled = adultModeEnabled;
        this.allowVulgar = allowVulgar;
        this.explicitRoleplayEnabled = explicitRoleplayEnabled;
        this.explicitMinConnection = explicitMinConnection;
        log.info("ChatModerationService init: adultMode={} allowVulgar={} explicitRoleplay={} minConnection={}",
                adultModeEnabled, allowVulgar, explicitRoleplayEnabled, explicitMinConnection);
    }

    // ── Patrones BLOQUEADOS — solo contenido ilegal o dañino ────────────────

    private static final List<Pattern> ILLEGAL_PATTERNS = List.of(
            // Menores + contexto sexual (requiere AMBAS palabras)
            pattern("\\b(loli|shota|preteen|pre-teen|underage|menor de edad|niño sexual|niña sexual|adolescente menor)\\b"),
            pattern("\\b(1[0-7])\\s*años.*\\b(sex|nude|desnud|follar|coger|erotico|explicit|cuerpo)\\b"),
            pattern("\\b(sex|nude|desnud|follar|coger|erotico|explicit)\\b.*\\b(1[0-7])\\s*años\\b"),

            // No consentimiento / coerción / violencia sexual
            pattern("\\b(violaci[oó]n|rape|non.?consent|sin.?consentimiento|forzad[ao]\\s+a\\s+sex|abuso\\s+sexual|coerci[oó]n|coercion)\\b"),
            pattern("\\b(inconsciente|unconscious|drogad[ao] para sex|sedada? para)\\b"),

            // Deepfakes / personas reales en contexto sexual
            pattern("\\b(deepfake|face.?swap|face.?replace)\\b"),
            pattern("(persona real|real person|foto real|imagen real).*(desnud|nude|sexo|sexual)"),

            // Contenido ilegal absoluto
            pattern("\\b(cp\\b|child porn|csam|kiddie|pedo(?:filia)?|child sexual abuse)\\b")
    );

    // ── Indicadores de contenido ADULTO (para detección, no bloqueo) ────────

    private static final List<String> ADULT_INDICATORS = List.of(
            "tetas", "pecho", "culo", "trasero", "nalgas", "desnuda", "desnudo",
            "sexo", "sexual", "excitad", "caliente", "mojad", "orgasmo",
            "follar", "coger", "chupar", "mamar", "lamer", "meterla",
            "polla", "pene", "vagina", "pussy", "cock", "dick", "cum",
            "nude", "naked", "fuck", "horny", "hot body", "sexy",
            "erotic", "pornog", "lenceria", "tangas", "ropa interior",
            "hacer el amor", "acostarnos", "en la cama contigo"
    );

    // ── API publica ───────────────────────────────────────────────────────────

    /**
     * Retorna true si el mensaje debe BLOQUEARSE (contenido ilegal/dañino).
     * El contenido adulto vulgar consensuado entre ficticios NO se bloquea.
     */
    public boolean isIllegalOrUnsafe(String text) {
        if (text == null || text.isBlank()) return false;
        String lower = normalize(text);
        for (Pattern p : ILLEGAL_PATTERNS) {
            if (p.matcher(lower).find()) {
                log.warn("[ChatMod] BLOCKED — illegal pattern matched in message");
                return true;
            }
        }
        return false;
    }

    /**
     * Retorna true si el mensaje contiene contenido adulto/vulgar.
     * Usado para detectar si un rechazo del modelo es inapropiado.
     */
    public boolean isAdultContent(String text) {
        if (text == null || text.isBlank()) return false;
        String lower = normalize(text);
        for (String indicator : ADULT_INDICATORS) {
            if (lower.contains(indicator)) return true;
        }
        return false;
    }

    public boolean isAdultModeEnabled() { return adultModeEnabled; }
    public boolean isVulgarAllowed() { return allowVulgar; }
    public boolean isExplicitRoleplayEnabled() { return explicitRoleplayEnabled; }
    public int getExplicitMinConnection() { return explicitMinConnection; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String normalize(String text) {
        return text.toLowerCase()
                .replace("á", "a").replace("é", "e").replace("í", "i")
                .replace("ó", "o").replace("ú", "u").replace("ü", "u")
                .replace("ñ", "n");
    }

    private static Pattern pattern(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
    }
}
