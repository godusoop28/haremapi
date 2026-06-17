package com.harems.api.image;

import com.harems.api.character.Character;
import com.harems.api.message.Message;
import com.harems.api.message.SenderType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Analyzes the recent conversation between user and character to determine
 * the appropriate image context: adult level, mood, scene and pose intent.
 *
 * The analysis uses the last N messages plus the user's optional prompt to
 * infer what the generated image should look and feel like, making it a
 * visual continuation of the conversation rather than a generic portrait.
 */
@Slf4j
@Service
public class ConversationImageContextBuilder {

    // ── Keyword lists ────────────────────────────────────────────────────────

    private static final List<String> NUDE_KEYWORDS = List.of(
            "sin ropa", "desnuda", "desnudo", "desnúdate", "quítate la ropa", "quítate todo",
            "fuera la ropa", "encuerada", "sin nada puesto", "nude", "naked", "no clothes",
            "sin ropa interior", "completamente desnuda"
    );

    private static final List<String> EXPLICIT_KEYWORDS = List.of(
            "escena sexual", "escena adulta", "acto sexual", "sexo explícito",
            "escena explícita", "adult scene", "sexual scene"
    );

    private static final List<String> SENSUAL_KEYWORDS = List.of(
            "sensual", "provocativa", "atrevida", "seductora", "sexy", "sugerente",
            "íntima", "íntimo", "lencería", "ropa interior", "bikini", "provocadora",
            "en actitud", "pose seductora"
    );

    // Scene keywords → scene description (checked in conversation content)
    private static final Map<String, String> SCENE_KEYWORD_MAP = Map.ofEntries(
            Map.entry("habitación", "intimate bedroom"),
            Map.entry("cama", "bed in intimate bedroom"),
            Map.entry("recámara", "intimate bedroom"),
            Map.entry("dormitorio", "intimate bedroom"),
            Map.entry("playa", "beach at sunset"),
            Map.entry("piscina", "poolside"),
            Map.entry("alberca", "poolside"),
            Map.entry("baño", "elegant private bathroom with soft lighting"),
            Map.entry("ducha", "shower with steam and soft light"),
            Map.entry("gimnasio", "modern gym with mirrors"),
            Map.entry("oficina", "private elegant office"),
            Map.entry("consultorio", "elegant private medical office"),
            Map.entry("terraza", "private rooftop terrace at night"),
            Map.entry("balcón", "hotel balcony at night with city lights"),
            Map.entry("sofá", "cozy living room with warm lighting"),
            Map.entry("sala", "elegant living room"),
            Map.entry("jardín", "private garden at golden hour"),
            Map.entry("galería", "dark alternative gallery"),
            Map.entry("cocina", "modern kitchen with warm light"),
            Map.entry("hotel", "luxury hotel suite")
    );

    // AI message keywords → mood
    private static final Map<String, String> MOOD_KEYWORD_MAP = Map.ofEntries(
            Map.entry("elegante", "elegant dominant"),
            Map.entry("sofisticada", "sophisticated elegant"),
            Map.entry("juguetona", "playful warm"),
            Map.entry("traviesa", "playful mischievous"),
            Map.entry("misteriosa", "mysterious dark"),
            Map.entry("oscura", "dark atmospheric"),
            Map.entry("dominante", "dominant authoritative"),
            Map.entry("autoridad", "dominant authoritative"),
            Map.entry("tímida", "shy tender"),
            Map.entry("nerviosa", "shy tender"),
            Map.entry("íntima", "intimate romantic"),
            Map.entry("romántica", "intimate romantic"),
            Map.entry("cálida", "warm tender"),
            Map.entry("directa", "confident direct"),
            Map.entry("segura", "confident"),
            Map.entry("intensa", "intense"),
            Map.entry("melancólica", "melancholic intense"),
            Map.entry("fría", "cold controlled"),
            Map.entry("calculadora", "cold controlled")
    );

    // Default scene per character slug
    private static final Map<String, String> CHARACTER_DEFAULT_SCENES = Map.ofEntries(
            Map.entry("luna-valmont",    "cozy warm bedroom with rose and cream decor, soft golden light"),
            Map.entry("hana-mori",       "rooftop terrace at night with city neon lights"),
            Map.entry("aurora-sterling", "luxury hotel suite with marble and velvet decor, evening candlelight"),
            Map.entry("valeria-cruz",    "elegant tropical rooftop lounge with warm amber light"),
            Map.entry("camila-rios",     "private home study with bookshelves and warm desk lamp"),
            Map.entry("kiara-blake",     "gaming room with ambient RGB lighting"),
            Map.entry("isabella-laurent","upscale private office or elegant hotel suite"),
            Map.entry("nara-voss",       "moody dim bedroom with one lamp, rain on the window"),
            Map.entry("sasha-monroe",    "modern light-filled bedroom with large windows or beach"),
            Map.entry("mei-tanaka",      "soft-lit cozy bedroom with pastel tones and fairy lights"),
            Map.entry("renata-soler",    "minimalist modern apartment or contemporary art studio"),
            Map.entry("victoria-hale",   "candlelit private hotel suite, intimate and elegant")
    );

    // Default mood per character slug
    private static final Map<String, String> CHARACTER_DEFAULT_MOODS = Map.ofEntries(
            Map.entry("luna-valmont",    "warm playful"),
            Map.entry("hana-mori",       "energetic bold"),
            Map.entry("aurora-sterling", "dominant elegant"),
            Map.entry("valeria-cruz",    "confident sensual"),
            Map.entry("camila-rios",     "focused subtle tension"),
            Map.entry("kiara-blake",     "edgy competitive"),
            Map.entry("isabella-laurent","authoritative mature dominant"),
            Map.entry("nara-voss",       "mysterious dark intense"),
            Map.entry("sasha-monroe",    "athletic energetic confident"),
            Map.entry("mei-tanaka",      "shy tender"),
            Map.entry("renata-soler",    "free natural uninhibited"),
            Map.entry("victoria-hale",   "contained intense emotionally charged")
    );

    // ── Public API ────────────────────────────────────────────────────────────

    public ImageContextAnalysis analyze(
            Character character,
            List<Message> recentMessages,
            String userPrompt,
            AdultLevel requestedLevel,
            int totalMessageCount
    ) {
        String lastAiMessage   = getLastMessageBySender(recentMessages, SenderType.AI);
        String lastUserMessage = getLastMessageBySender(recentMessages, SenderType.USER);

        AdultLevel adultLevel = detectAdultLevel(userPrompt, lastUserMessage, requestedLevel);
        String mood            = detectMood(character, lastAiMessage);
        String scene           = detectScene(character, lastAiMessage, lastUserMessage, userPrompt);
        String poseIntent      = derivePoseIntent(adultLevel, mood);

        int threshold  = trustThreshold(character);
        boolean highTrust = totalMessageCount >= threshold;

        String summary = String.format(
                "char=%s level=%s mood=%s scene=%s msgs=%d threshold=%d highTrust=%s",
                character.getSlug(), adultLevel, mood, scene,
                totalMessageCount, threshold, highTrust);

        log.info("[ImageContext] {}", summary);

        return new ImageContextAnalysis(adultLevel, mood, scene, poseIntent, summary, highTrust, totalMessageCount);
    }

    /** Threshold de mensajes (totales, no solo los recientes) según dificultad del personaje. */
    private int trustThreshold(Character character) {
        String d = character.getDifficulty() != null ? character.getDifficulty().toLowerCase() : "";
        if (d.contains("extrema"))                            return 50;
        if (d.contains("muy alta"))                           return 30;
        if (d.contains("alta") && d.contains("media"))       return 20;
        if (d.contains("alta"))                               return 24;
        if (d.contains("fácil") && d.contains("media"))      return 8;
        if (d.contains("media") && !d.contains("alta"))      return 14;
        if (d.contains("fácil"))                              return 5;
        return 12;
    }

    // ── Detection helpers ─────────────────────────────────────────────────────

    private AdultLevel detectAdultLevel(String userPrompt, String lastUserMessage, AdultLevel requested) {
        if (requested != null) return requested;

        // Check userPrompt first (explicit request takes priority)
        if (hasKeyword(userPrompt, NUDE_KEYWORDS))     return AdultLevel.NUDE;
        if (hasKeyword(userPrompt, EXPLICIT_KEYWORDS)) return AdultLevel.EXPLICIT;
        if (hasKeyword(userPrompt, SENSUAL_KEYWORDS))  return AdultLevel.SENSUAL;

        // Fall back to last user message in conversation
        if (hasKeyword(lastUserMessage, NUDE_KEYWORDS))     return AdultLevel.NUDE;
        if (hasKeyword(lastUserMessage, EXPLICIT_KEYWORDS)) return AdultLevel.EXPLICIT;
        if (hasKeyword(lastUserMessage, SENSUAL_KEYWORDS))  return AdultLevel.SENSUAL;

        // Default: SENSUAL — adult platform, better than a plain portrait
        return AdultLevel.SENSUAL;
    }

    private String detectMood(Character character, String lastAiMessage) {
        if (lastAiMessage != null && !lastAiMessage.isBlank()) {
            String lower = lastAiMessage.toLowerCase();
            for (Map.Entry<String, String> entry : MOOD_KEYWORD_MAP.entrySet()) {
                if (lower.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        return CHARACTER_DEFAULT_MOODS.getOrDefault(character.getSlug(), "confident sensual");
    }

    private String detectScene(Character character, String lastAiMessage, String lastUserMessage, String userPrompt) {
        // userPrompt wins: "en la playa" etc.
        String fromUserPrompt = findSceneInText(userPrompt);
        if (fromUserPrompt != null) return fromUserPrompt;

        // Last user message second
        String fromLastUser = findSceneInText(lastUserMessage);
        if (fromLastUser != null) return fromLastUser;

        // Last AI message third
        String fromLastAi = findSceneInText(lastAiMessage);
        if (fromLastAi != null) return fromLastAi;

        // Character default
        return CHARACTER_DEFAULT_SCENES.getOrDefault(character.getSlug(), "intimate private room with soft lighting");
    }

    private String findSceneInText(String text) {
        if (text == null || text.isBlank()) return null;
        String lower = text.toLowerCase();
        for (Map.Entry<String, String> entry : SCENE_KEYWORD_MAP.entrySet()) {
            if (lower.contains(entry.getKey())) return entry.getValue();
        }
        return null;
    }

    private String derivePoseIntent(AdultLevel level, String mood) {
        return switch (level) {
            case SAFE     -> "natural elegant standing or sitting pose";
            case SENSUAL  -> "alluring confident pose, slightly revealing";
            case NUDE     -> "relaxed natural nude pose, confident and comfortable";
            case EXPLICIT -> "adult explicit consensual pose";
        };
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private String getLastMessageBySender(List<Message> messages, SenderType sender) {
        if (messages == null || messages.isEmpty()) return "";
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).getSender() == sender) {
                return messages.get(i).getContent();
            }
        }
        return "";
    }

    private boolean hasKeyword(String text, List<String> keywords) {
        if (text == null || text.isBlank()) return false;
        String lower = text.toLowerCase();
        for (String kw : keywords) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) + "…" : text;
    }
}
