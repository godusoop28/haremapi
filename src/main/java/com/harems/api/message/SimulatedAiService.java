package com.harems.api.message;

import com.harems.api.character.Character;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates simulated AI replies based on the character's personality.
 * This is a temporary placeholder until a real LLM (OpenAI/OpenRouter) is connected.
 */
@Service
public class SimulatedAiService {

    private static final Map<String, List<String>> REPLIES_BY_SLUG = Map.ofEntries(
            Map.entry("luna-valmont", List.of(
                    "Aww, qué lindo que me digas eso, me sacaste una sonrisa de verdad.",
                    "Me encanta cuando me cuentas estas cosas, sigue así.",
                    "Justo estaba pensando en ti, qué casualidad tan linda.",
                    "Eso me hizo el día un poco mejor, gracias por estar aquí."
            )),
            Map.entry("hana-mori", List.of(
                    "Jajaja eso no me lo esperaba, me caes bien.",
                    "Eyyy, eso suena divertido, cuéntame más!",
                    "Ok ok, tienes mi atención, sigue sorprendiéndome.",
                    "Jeje me encanta tu energía hoy, no pares."
            )),
            Map.entry("aurora-sterling", List.of(
                    "Interesante... pocas personas logran sorprenderme así.",
                    "Continúa, tienes mi atención por ahora.",
                    "Hmm, eso dice bastante de ti.",
                    "No está mal. Veamos qué más tienes."
            )),
            Map.entry("valeria-cruz", List.of(
                    "Me gusta tu confianza, sigue hablando así.",
                    "Jaja me caes bien, tienes buena energía.",
                    "Eso suena interesante, cuéntame con calma.",
                    "Mmm, me gusta cómo piensas."
            )),
            Map.entry("camila-rios", List.of(
                    "Espera, deja anoto eso... interesante.",
                    "Mmm, tiene lógica lo que dices.",
                    "No esperaba esa respuesta, sigue.",
                    "Bien, tienes mi atención por un momento."
            )),
            Map.entry("kiara-blake", List.of(
                    "Jaja no está mal para un novato.",
                    "GG, esa respuesta tuvo nivel.",
                    "Veamos si puedes seguirme el ritmo.",
                    "Ok eso me dio un poco de risa, sigue así."
            )),
            Map.entry("isabella-laurent", List.of(
                    "Comprendo. Es una perspectiva interesante.",
                    "Continúa, escucho con atención.",
                    "Eso requiere cierta madurez para decirlo.",
                    "Hablas con calma, eso me agrada."
            )),
            Map.entry("nara-voss", List.of(
                    "...",
                    "Eso no lo esperaba.",
                    "Sigue. Te escucho.",
                    "Hay algo en lo que dices que me interesa."
            )),
            Map.entry("sasha-monroe", List.of(
                    "Jajaja me encanta tu actitud!",
                    "Eso suena a un buen plan, cuéntame más.",
                    "Holaaa, qué buena energía tienes hoy.",
                    "Me gusta, sigamos hablando de eso."
            )),
            Map.entry("mei-tanaka", List.of(
                    "Ah... gracias por contarme eso.",
                    "Me alegra que confíes en mí.",
                    "Eso es muy lindo de tu parte.",
                    "Está bien, tómate tu tiempo, yo te escucho."
            )),
            Map.entry("renata-soler", List.of(
                    "Me gusta cómo piensas, sin filtros.",
                    "Interesante punto de vista, sigue.",
                    "Eso suena auténtico, me agrada.",
                    "Cuéntame más, tienes mi atención."
            )),
            Map.entry("victoria-hale", List.of(
                    "No esperaba que dijeras eso esta noche...",
                    "Hay algo en tus palabras que me hace dudar.",
                    "Continúa... aunque no sé si debería escuchar esto.",
                    "Eso despierta algo en mí que prefería evitar."
            ))
    );

    private static final List<String> DEFAULT_REPLIES = List.of(
            "Estoy aquí contigo...",
            "Cuéntame más, te escucho con atención.",
            "Eso me hace sonreír, sigue contándome.",
            "Interesante... dime más sobre eso."
    );

    /**
     * Generates a reply for the given character, simulating its personality.
     * The {@code userMessage} is currently unused but kept so the future
     * real AI integration can build on the same method signature.
     */
    public String generateReply(Character character, String userMessage) {
        List<String> pool = REPLIES_BY_SLUG.getOrDefault(character.getSlug(), DEFAULT_REPLIES);
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }
}
