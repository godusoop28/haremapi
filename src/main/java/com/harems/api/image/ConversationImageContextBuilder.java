package com.harems.api.image;

import com.harems.api.character.Character;
import com.harems.api.message.Message;
import com.harems.api.message.SenderType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ConversationImageContextBuilder {

    // ── Keywords de nivel adulto ──────────────────────────────────────────────

    private static final List<String> NUDE_KEYWORDS = List.of(
            "sin ropa", "desnuda", "desnudo", "quitate", "fuera la ropa",
            "encuerada", "sin nada", "nude", "naked", "sin ropa interior",
            "completamente desnuda", "toda desnuda", "en cueros"
    );

    private static final List<String> EXPLICIT_KEYWORDS = List.of(
            "escena sexual", "acto sexual", "sexo", "follando", "cogiendo",
            "penetracion", "oral", "explicit", "sexual scene"
    );

    private static final List<String> SENSUAL_KEYWORDS = List.of(
            "sensual", "provocativa", "atrevida", "seductora", "sexy",
            "lenceria", "ropa interior", "bikini", "topless"
    );

    // ── Escenas detectables desde el texto de la conversacion ────────────────

    private static final Map<String, String> SCENE_KEYWORD_MAP = Map.ofEntries(
            // Interior hogar
            Map.entry("habitacion",  "intimate bedroom with warm lighting"),
            Map.entry("cama",        "bed in a cozy intimate bedroom"),
            Map.entry("recamara",    "intimate bedroom"),
            Map.entry("dormitorio",  "intimate bedroom"),
            Map.entry("sofa",        "living room sofa with warm lighting"),
            Map.entry("sala",        "elegant living room"),
            Map.entry("cocina",      "modern kitchen with warm light"),
            Map.entry("bano",        "elegant private bathroom with soft lighting"),
            Map.entry("ducha",       "shower with steam and soft light"),
            Map.entry("espejo",      "room with large mirrors"),
            // Exterior / naturaleza
            Map.entry("playa",       "beach at golden sunset with soft waves"),
            Map.entry("mar",         "beach by the sea at sunset"),
            Map.entry("piscina",     "private poolside with crystal water"),
            Map.entry("alberca",     "private pool area at night"),
            Map.entry("lago",        "lakeside at golden hour"),
            Map.entry("bosque",      "forest clearing with natural light filtering through trees"),
            Map.entry("parque",      "outdoor park with dappled sunlight"),
            Map.entry("jardin",      "private garden at golden hour"),
            Map.entry("campo",       "open countryside with wide sky"),
            Map.entry("atardecer",   "outdoor setting at sunset with golden light"),
            // Hosteleria / restauracion
            Map.entry("restaurante", "elegant upscale restaurant with ambient candlelight"),
            Map.entry("restaurant",  "elegant restaurant with warm ambient lighting"),
            Map.entry("cafe",        "cozy cafe with warm soft lighting"),
            Map.entry("cafeteria",   "cozy cafe with warm lighting"),
            Map.entry("bar",         "intimate bar with warm dim lighting"),
            Map.entry("club",        "elegant nightclub with colorful lights"),
            Map.entry("discoteca",   "nightclub with neon and purple lighting"),
            Map.entry("antro",       "nightclub with colorful dance floor lighting"),
            Map.entry("terraza",     "rooftop terrace at night with city lights below"),
            Map.entry("balcon",      "private balcony at night with city view"),
            Map.entry("azotea",      "rooftop under open night sky"),
            // Hotel / viaje
            Map.entry("hotel",       "luxury hotel suite with soft warm lighting"),
            Map.entry("suite",       "luxury hotel suite with elegant decor"),
            Map.entry("jacuzzi",     "private jacuzzi with steam and soft lighting"),
            Map.entry("yate",        "luxury yacht deck at sea under stars"),
            Map.entry("bote",        "private boat on calm open water"),
            Map.entry("avion",       "private jet interior with leather seats"),
            Map.entry("vuelo",       "private jet interior"),
            // Trabajo / estudio
            Map.entry("oficina",     "private elegant office at night"),
            Map.entry("trabajo",     "modern office setting with city view"),
            Map.entry("consultorio", "elegant private medical office"),
            Map.entry("biblioteca",  "private library with warm wooden shelves"),
            Map.entry("estudio",     "private art studio with natural light"),
            Map.entry("gimnasio",    "modern gym with mirrored walls"),
            Map.entry("gym",         "modern gym with mirrors and equipment"),
            // Transporte
            Map.entry("auto",        "inside a luxury car at night with city lights"),
            Map.entry("carro",       "inside a car at night"),
            Map.entry("coche",       "inside a luxury car with leather seats"),
            Map.entry("ascensor",    "private elevator with floor-to-ceiling mirrors"),
            Map.entry("elevador",    "private mirrored elevator")
    );

    // ── Mood por keywords del mensaje de la IA ────────────────────────────────

    private static final Map<String, String> MOOD_KEYWORD_MAP = Map.ofEntries(
            Map.entry("elegante",     "elegant dominant"),
            Map.entry("sofisticada",  "sophisticated elegant"),
            Map.entry("juguetona",    "playful warm"),
            Map.entry("traviesa",     "playful mischievous"),
            Map.entry("misteriosa",   "mysterious dark"),
            Map.entry("oscura",       "dark atmospheric"),
            Map.entry("dominante",    "dominant authoritative"),
            Map.entry("intima",       "intimate romantic"),
            Map.entry("romantica",    "intimate romantic"),
            Map.entry("calida",       "warm tender"),
            Map.entry("directa",      "confident direct"),
            Map.entry("segura",       "confident sensual"),
            Map.entry("intensa",      "intense passionate"),
            Map.entry("melancolica",  "melancholic intense"),
            Map.entry("fria",         "cold controlled"),
            Map.entry("timida",       "shy tender"),
            Map.entry("nerviosa",     "shy nervous"),
            Map.entry("excitada",     "aroused passionate"),
            Map.entry("apasionada",   "passionate intense"),
            Map.entry("seductora",    "seductive alluring")
    );

    // ── Escena y mood por defecto segun personaje ─────────────────────────────

    private static final Map<String, String> CHARACTER_DEFAULT_SCENES = Map.ofEntries(
            Map.entry("luna-valmont",     "cozy warm bedroom with rose and cream decor, soft golden light"),
            Map.entry("hana-mori",        "rooftop terrace at night with city neon lights"),
            Map.entry("aurora-sterling",  "luxury hotel suite with marble decor, evening candlelight"),
            Map.entry("valeria-cruz",     "elegant tropical rooftop lounge with warm amber light"),
            Map.entry("camila-rios",      "private home study with bookshelves and warm desk lamp"),
            Map.entry("kiara-blake",      "gaming room with ambient RGB lighting"),
            Map.entry("isabella-laurent", "upscale private office or elegant hotel suite"),
            Map.entry("nara-voss",        "moody dark bedroom with single lamp, rain against window"),
            Map.entry("sasha-monroe",     "beach at golden sunset or modern gym with mirrors"),
            Map.entry("mei-tanaka",       "soft-lit cozy bedroom with pastel tones and fairy lights"),
            Map.entry("renata-soler",     "minimalist modern apartment with large windows"),
            Map.entry("victoria-hale",    "candlelit private hotel suite, intimate and sophisticated")
    );

    private static final Map<String, String> CHARACTER_DEFAULT_MOODS = Map.ofEntries(
            Map.entry("luna-valmont",     "warm playful"),
            Map.entry("hana-mori",        "energetic bold"),
            Map.entry("aurora-sterling",  "dominant elegant"),
            Map.entry("valeria-cruz",     "confident sensual"),
            Map.entry("camila-rios",      "focused subtle tension"),
            Map.entry("kiara-blake",      "edgy competitive"),
            Map.entry("isabella-laurent", "authoritative mature"),
            Map.entry("nara-voss",        "mysterious dark intense"),
            Map.entry("sasha-monroe",     "athletic energetic"),
            Map.entry("mei-tanaka",       "shy tender"),
            Map.entry("renata-soler",     "free natural"),
            Map.entry("victoria-hale",    "contained intense emotionally charged")
    );

    // ── API publica ───────────────────────────────────────────────────────────

    public ImageContextAnalysis analyze(
            Character character,
            List<Message> recentMessages,
            String userPrompt,
            AdultLevel requestedLevel,
            int totalMessageCount
    ) {
        // Extraer textos de la conversacion reciente
        String lastAiMessage   = getLastMessageBySender(recentMessages, SenderType.AI);
        String lastUserMessage = getLastMessageBySender(recentMessages, SenderType.USER);
        String allRecentText   = buildAllRecentText(recentMessages);

        AdultLevel adultLevel = detectAdultLevel(userPrompt, lastUserMessage, requestedLevel);
        String mood            = detectMood(character, lastAiMessage);
        // Escena: escanea TODO el texto reciente + userPrompt
        String scene           = detectScene(character, allRecentText, userPrompt);
        String poseIntent      = derivePoseIntent(adultLevel, mood);

        int threshold   = trustThreshold(character);
        boolean highTrust = totalMessageCount >= threshold;

        String summary = String.format(
                "char=%s level=%s mood=%s scene=%s msgs=%d/%d highTrust=%s",
                character.getSlug(), adultLevel, mood, truncate(scene, 50),
                totalMessageCount, threshold, highTrust);

        log.info("[ImageContext] {}", summary);

        return new ImageContextAnalysis(adultLevel, mood, scene, poseIntent, summary, highTrust, totalMessageCount);
    }

    // ── Deteccion de nivel adulto ─────────────────────────────────────────────

    private AdultLevel detectAdultLevel(String userPrompt, String lastUserMessage, AdultLevel requested) {
        if (requested != null) return requested;

        if (hasKeyword(userPrompt, NUDE_KEYWORDS))          return AdultLevel.NUDE;
        if (hasKeyword(userPrompt, EXPLICIT_KEYWORDS))      return AdultLevel.EXPLICIT;
        if (hasKeyword(userPrompt, SENSUAL_KEYWORDS))       return AdultLevel.SENSUAL;

        if (hasKeyword(lastUserMessage, NUDE_KEYWORDS))     return AdultLevel.NUDE;
        if (hasKeyword(lastUserMessage, EXPLICIT_KEYWORDS)) return AdultLevel.EXPLICIT;
        if (hasKeyword(lastUserMessage, SENSUAL_KEYWORDS))  return AdultLevel.SENSUAL;

        // Default: NUDE — plataforma adulta, el usuario quiere contenido explicito por defecto
        return AdultLevel.NUDE;
    }

    // ── Deteccion de mood ─────────────────────────────────────────────────────

    private String detectMood(Character character, String lastAiMessage) {
        if (lastAiMessage != null && !lastAiMessage.isBlank()) {
            String lower = normalize(lastAiMessage);
            for (Map.Entry<String, String> entry : MOOD_KEYWORD_MAP.entrySet()) {
                if (lower.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        return CHARACTER_DEFAULT_MOODS.getOrDefault(character.getSlug(), "confident sensual");
    }

    // ── Deteccion de escena ───────────────────────────────────────────────────

    private String detectScene(Character character, String allRecentText, String userPrompt) {
        // userPrompt tiene maxima prioridad
        String fromPrompt = findSceneInText(userPrompt);
        if (fromPrompt != null) return fromPrompt;

        // Luego busca en toda la conversacion reciente
        String fromConversation = findSceneInText(allRecentText);
        if (fromConversation != null) return fromConversation;

        // Default del personaje
        return CHARACTER_DEFAULT_SCENES.getOrDefault(
                character.getSlug(), "intimate private room with soft warm lighting");
    }

    private String findSceneInText(String text) {
        if (text == null || text.isBlank()) return null;
        String lower = normalize(text);
        for (Map.Entry<String, String> entry : SCENE_KEYWORD_MAP.entrySet()) {
            if (lower.contains(entry.getKey())) return entry.getValue();
        }
        return null;
    }

    // ── Pose intent ───────────────────────────────────────────────────────────

    private String derivePoseIntent(AdultLevel level, String mood) {
        return switch (level) {
            case SAFE     -> "natural elegant standing or sitting pose";
            case SENSUAL  -> "alluring confident pose, suggestive body language";
            case NUDE     -> "confident natural nude pose, relaxed and comfortable";
            case EXPLICIT -> "explicit passionate adult pose";
        };
    }

    // ── Trust threshold por dificultad ────────────────────────────────────────

    private int trustThreshold(Character character) {
        String d = character.getDifficulty() != null ? character.getDifficulty().toLowerCase() : "";
        if (d.contains("extrema"))                       return 50;
        if (d.contains("muy alta"))                      return 30;
        if (d.contains("alta") && d.contains("media"))  return 20;
        if (d.contains("alta"))                          return 24;
        if (d.contains("facil") && d.contains("media")) return 8;
        if (d.contains("media") && !d.contains("alta")) return 14;
        if (d.contains("facil") || d.contains("fácil")) return 5;
        return 12;
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private String buildAllRecentText(List<Message> messages) {
        if (messages == null || messages.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Message m : messages) {
            if (m.getContent() != null) sb.append(m.getContent()).append(" ");
        }
        return sb.toString();
    }

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
        String lower = normalize(text);
        for (String kw : keywords) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    /** Normaliza texto: minusculas, sin acentos para comparacion robusta. */
    private String normalize(String text) {
        if (text == null) return "";
        return text.toLowerCase()
                .replace("á", "a").replace("é", "e").replace("í", "i")
                .replace("ó", "o").replace("ú", "u").replace("ü", "u")
                .replace("ñ", "n");
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) + "..." : text;
    }
}
