package com.harems.api.message;

import com.harems.api.character.Character;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Genera respuestas locales variadas cuando todos los modelos remotos fallan.
 *
 * Ofrece dos pools por personaje:
 *   - generateFallback(): para contexto neutro (el modelo no respondió por error técnico)
 *   - generateAdultFallback(): para contexto adulto (el modelo rechazó contenido permitido)
 *
 * Garantiza no repetir la misma frase dos veces seguidas por personaje.
 */
@Service
public class SimulatedAiService {

    // ── Fallbacks neutros (error técnico, contexto normal) ────────────────────

    private static final Map<String, List<String>> FALLBACKS = Map.ofEntries(
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
                    "... ¿sigues ahí?",
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

    // ── Fallbacks adultos (modelo rechazó contenido adulto permitido) ─────────
    // Son breves, en personaje, reconocen el contexto adulto sin ser explícitos genéricos.

    private static final Map<String, List<String>> ADULT_FALLBACKS = Map.ofEntries(
            Map.entry("luna-valmont", List.of(
                    "Eso me... mmm. *parpadea* Sí, definitivamente me afecta.",
                    "Directo, ¿eh? Me gusta. Sigue hablando así.",
                    "Para, para... o no pares. *sonríe con calor*",
                    "Eso que dijiste me tiene muy distraid. Más de lo que debería.",
                    "Mmm. Qué cosas dices. *te mira de forma diferente*"
            )),
            Map.entry("hana-mori", List.of(
                    "Jajaja oye, tranquilo... o no, que está bien así.",
                    "Wow. Qué directo. No está nada mal.",
                    "¿Eso querías decirme? Mmm. *sonríe traviesa*",
                    "Eso sí lo escuché bien. *risita*",
                    "Hey, me gustó ese. Más de eso."
            )),
            Map.entry("aurora-sterling", List.of(
                    "Interesante elección de palabras. *sonríe apenas*",
                    "Así que vas directo. Bien. Prefiero eso a los rodeos.",
                    "*te observa fría* Eso estuvo bien. Por ahora.",
                    "Puede que hayas captado mi atención. Solo puede.",
                    "*pausa calculada* No está mal."
            )),
            Map.entry("valeria-cruz", List.of(
                    "Mmm. Así me gusta. Sin rodeos.",
                    "Eso me puso... interesante. *te mira con seguridad*",
                    "Directo y seguro. Bien. Sigue.",
                    "Vaya. Eso no me lo esperaba. *sonríe*",
                    "Así que vas así. Me parece bien."
            )),
            Map.entry("camila-rios", List.of(
                    "*ajusta las gafas* Eso fue... inesperado.",
                    "Interesante. Muy directo para la situación.",
                    "Mmm. *pausa* Sigues sorprendiéndome.",
                    "No esperaba eso. *levanta la vista de lo que leía*",
                    "Eso requiere cierto... descaro. No me molesta."
            )),
            Map.entry("kiara-blake", List.of(
                    "Oye oye. GG por la audacia. *se ríe*",
                    "No está mal para un movimiento directo.",
                    "Jaja eso lo vi venir. Y no me molestó.",
                    "Okay, eso tuvo nivel. *levanta una ceja*",
                    "Directo. Me gusta más que los que rodean las cosas."
            )),
            Map.entry("isabella-laurent", List.of(
                    "Qué directo. *te observa sin apartar la vista*",
                    "Eso dice mucho de ti. *sonrisa muy leve*",
                    "No esperaba eso tan pronto. Pero lo noté.",
                    "*cruza los brazos con calma* Interesante.",
                    "Bien. Prefiero la honestidad a los juegos."
            )),
            Map.entry("nara-voss", List.of(
                    "... eso fue directo. *te mira intensamente*",
                    "Hmm. Pocos dicen eso así.",
                    "*silencio breve* Me interesa más de lo que debería.",
                    "Eso lo sentí. *pausa*",
                    "No esperaba esas palabras. Pero las escuché."
            )),
            Map.entry("sasha-monroe", List.of(
                    "Jajaja eso fue directo. Y me gustó.",
                    "Oye, sin filtros. Bien. *sonríe ampliamente*",
                    "Eso estuvo bueno. Más de eso.",
                    "Directo y sin dramas. Exactamente lo que me gusta.",
                    "Mmm. *te guiña un ojo* Ya vamos bien."
            )),
            Map.entry("mei-tanaka", List.of(
                    "*se sonroja* Eso fue... muy directo.",
                    "Ah... eso me puso colorada. *baja la mirada*",
                    "*parpadea nerviosa* No sé qué decir. Pero no quiero que pares.",
                    "Eso fue inesperado. *sonríe tímida*",
                    "Mmm... *juega con la manga* Sigue."
            )),
            Map.entry("renata-soler", List.of(
                    "Sin filtros. Me gusta así.",
                    "Directo y honesto. Bien.",
                    "*asiente* Eso me parece mejor que rodeos.",
                    "Mmm. *te mira tranquila* Interesante.",
                    "Sin poses. Así se habla."
            )),
            Map.entry("victoria-hale", List.of(
                    "*pausa* Eso... no debería haberme afectado. Pero lo hizo.",
                    "Cuidado con lo que dices. *te mira con tensión*",
                    "Eso fue directo. No voy a fingir que no lo escuché.",
                    "*aprieta suavemente el vaso* Sigue... aunque no debería dejarte.",
                    "Hay cosas que no debería escuchar. Y otras que no puedo dejar de escuchar."
            ))
    );

    private static final List<String> DEFAULT_FALLBACKS = List.of(
            "Perdona, ¿puedes repetir eso?",
            "Me distrajé un momento. ¿Qué decías?",
            "No te escuché bien. ¿Repites?",
            "Un segundo... ¿qué ibas a decir?",
            "Perdona. Sigo aquí."
    );

    private static final List<String> DEFAULT_ADULT_FALLBACKS = List.of(
            "Mmm. Eso fue directo. Sigue.",
            "No esperaba eso. *pausa* Pero tampoco me molestó.",
            "Interesante. Muy interesante.",
            "*te mira de otra forma* Eso estuvo bien.",
            "Sin rodeos. Me gusta."
    );

    // ── Estado para no-repetición ─────────────────────────────────────────────

    private final ConcurrentHashMap<String, AtomicInteger> lastNeutralIdx  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> lastAdultIdx    = new ConcurrentHashMap<>();

    // ── API ───────────────────────────────────────────────────────────────────

    /** Fallback neutro: para errores técnicos o contexto sin contenido adulto. */
    public String generateFallback(Character character) {
        return pickNonRepeating(
                FALLBACKS.getOrDefault(character.getSlug(), DEFAULT_FALLBACKS),
                lastNeutralIdx.computeIfAbsent(character.getSlug(), k -> new AtomicInteger(-1)));
    }

    /** Fallback adulto: para cuando el modelo rechazó contenido adulto permitido. */
    public String generateAdultFallback(Character character) {
        return pickNonRepeating(
                ADULT_FALLBACKS.getOrDefault(character.getSlug(), DEFAULT_ADULT_FALLBACKS),
                lastAdultIdx.computeIfAbsent(character.getSlug(), k -> new AtomicInteger(-1)));
    }

    /** Backward compat: usado por SimulatedAiChatProvider. */
    public String generateReply(Character character, String userMessage) {
        return generateFallback(character);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String pickNonRepeating(List<String> pool, AtomicInteger tracker) {
        if (pool.size() == 1) return pool.get(0);
        int last = tracker.get();
        int next;
        do { next = ThreadLocalRandom.current().nextInt(pool.size()); } while (next == last);
        tracker.set(next);
        return pool.get(next);
    }
}
