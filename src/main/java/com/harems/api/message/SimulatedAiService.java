package com.harems.api.message;

import com.harems.api.character.Character;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generates varied local fallback replies when all remote AI models are unavailable.
 *
 * Each character has 5 in-character phrases. A non-repetition mechanism ensures
 * the same response is never returned twice in a row for the same character.
 */
@Service
public class SimulatedAiService {

    // 5 varied in-character phrases per character
    private static final Map<String, List<String>> FALLBACKS_BY_SLUG = Map.ofEntries(
            Map.entry("luna-valmont", List.of(
                    "Espera... me distrajé un segundo. ¿Por dónde íbamos?",
                    "*parpadea* Ay, perdona. ¿Qué me decías?",
                    "Un momento... quiero escucharte bien. ¿Repites eso?",
                    "Uy, se me fue la cabeza a otro lado. Sigue, sigo aquí.",
                    "*sonríe* Perdona, me perdí un poco. ¿Qué decías?"
            )),
            Map.entry("hana-mori", List.of(
                    "Oye, me lagueé un seg jaja. ¿Qué dijiste?",
                    "Eyyy, me desconecté. ¿Repites?",
                    "No te escuché bien. ¿Qué fue eso?",
                    "Momento, me fui. Sigue, que te estoy escuchando.",
                    "Jaja perdona, me distraje. ¿Qué pasó?"
            )),
            Map.entry("aurora-sterling", List.of(
                    "Disculpa la demora. ¿Podrías repetirlo?",
                    "*pausa* Me distrajé un momento. Continúa.",
                    "Perdona. Tenía la mente en otro lugar. ¿Qué ibas a decir?",
                    "*te mira* Repite, por favor. No estaba concentrada.",
                    "Un instante de distracción. Continúa."
            )),
            Map.entry("valeria-cruz", List.of(
                    "Eh, ¿qué dijiste? No te escuché bien.",
                    "Perdona, me fui un momento. ¿Repites?",
                    "Oye, me desconecté. ¿Qué fue eso?",
                    "*vuelve la atención* Sigo aquí. ¿Qué decías?",
                    "Me distraje un segundo. Cuéntame."
            )),
            Map.entry("camila-rios", List.of(
                    "Disculpa, estaba en otra cosa. ¿Qué decías?",
                    "Perdona. Me fui un momento. Repite, por favor.",
                    "*levanta la vista* Ah, perdona. ¿Qué ibas a decir?",
                    "Me perdí. ¿Puedes repetirlo?",
                    "Un momento de distracción. Sigo aquí."
            )),
            Map.entry("kiara-blake", List.of(
                    "Lag de conexión jaja. ¿Repites?",
                    "Oops, me fui un segundo. ¿Qué fue?",
                    "GG, me desconecté. ¿Qué dijiste?",
                    "Eyyy no te escuché. ¿Repites rápido?",
                    "*vuelve* Perdona el timeout. ¿Qué?"
            )),
            Map.entry("isabella-laurent", List.of(
                    "Disculpa la interrupción. ¿Podrías repetirlo?",
                    "*pausa breve* Me distraje. Continúa, por favor.",
                    "Perdona. ¿Qué ibas a decir?",
                    "Un momento de distracción. Sigo escuchando.",
                    "*te observa* Repite eso, por favor."
            )),
            Map.entry("nara-voss", List.of(
                    "... me perdí. Repite.",
                    "*silencio* ¿Qué dijiste?",
                    "No te escuché. ¿Repites?",
                    "... otro momento. ¿Sigues ahí?",
                    "Me fui un segundo. ¿Qué fue?"
            )),
            Map.entry("sasha-monroe", List.of(
                    "Oye sorry, me fui un seg. ¿Qué dijiste?",
                    "Perdona, me desconecté. ¿Repites?",
                    "Hey, no te escuché bien. ¿Qué fue eso?",
                    "Me distraje, sorry. Sigue.",
                    "*vuelve* ¿Qué me decías?"
            )),
            Map.entry("mei-tanaka", List.of(
                    "Ah... perdona, ¿qué ibas a decir?",
                    "*parpadea* Me perdí un momento. ¿Repites?",
                    "Disculpa... ¿qué dijiste?",
                    "Perdona, no te escuché bien. ¿Repites?",
                    "*baja la mirada* Ay, me distraje. ¿Qué decías?"
            )),
            Map.entry("renata-soler", List.of(
                    "Me fui un momento. ¿Qué ibas a decir?",
                    "Perdona. ¿Repites?",
                    "Me desconecté un segundo. Sigue.",
                    "*vuelve* ¿Qué fue eso?",
                    "No te escuché bien. ¿Qué dijiste?"
            )),
            Map.entry("victoria-hale", List.of(
                    "Perdona... me distraje. ¿Qué decías?",
                    "*pausa* No te escuché bien. Repite.",
                    "Disculpa. Un momento de distracción. ¿Qué ibas a decir?",
                    "Me fui un segundo. Continúa.",
                    "*mira hacia otro lado brevemente* Perdona. ¿Sigues ahí?"
            ))
    );

    private static final List<String> DEFAULT_FALLBACKS = List.of(
            "Perdona, ¿puedes repetir eso?",
            "Me distrajé un momento. ¿Qué decías?",
            "No te escuché bien. ¿Repites?",
            "Un segundo... ¿qué ibas a decir?",
            "Perdona. Sigo aquí."
    );

    // Per-character last-used index to avoid immediate repetition
    private final ConcurrentHashMap<String, AtomicInteger> lastIdx = new ConcurrentHashMap<>();

    /**
     * Returns a varied in-character fallback. Never repeats the same phrase twice in a row
     * for the same character slug.
     */
    public String generateFallback(Character character) {
        List<String> pool = FALLBACKS_BY_SLUG.getOrDefault(character.getSlug(), DEFAULT_FALLBACKS);
        AtomicInteger tracker = lastIdx.computeIfAbsent(character.getSlug(), k -> new AtomicInteger(-1));
        int last = tracker.get();
        int next;
        if (pool.size() == 1) {
            next = 0;
        } else {
            do {
                next = ThreadLocalRandom.current().nextInt(pool.size());
            } while (next == last);
        }
        tracker.set(next);
        return pool.get(next);
    }

    // Keep for backward compat (used by SimulatedAiChatProvider)
    public String generateReply(Character character, String userMessage) {
        return generateFallback(character);
    }
}
