package com.harems.api.ai;

import tools.jackson.databind.JsonNode;
import com.harems.api.character.Character;
import com.harems.api.message.Message;
import com.harems.api.message.SenderType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Calls the OpenRouter chat completions API to generate a real AI reply.
 */
@Slf4j
@Component
public class OpenRouterChatProvider implements AiChatProvider {

    private final RestClient restClient;
    private final String model;
    private final String baseUrl;
    private final String apiKey;
    private final double temperature;
    private final int maxTokens;
    private final double topP;
    private final double frequencyPenalty;
    private final double presencePenalty;

    public OpenRouterChatProvider(
            @Value("${ai.openrouter.api-key}") String apiKey,
            @Value("${ai.openrouter.model}") String model,
            @Value("${ai.openrouter.base-url}") String baseUrl,
            @Value("${ai.openrouter.referer}") String referer,
            @Value("${ai.openrouter.title}") String title,
            @Value("${ai.openrouter.timeout-ms}") long timeoutMs,
            @Value("${ai.openrouter.temperature}") double temperature,
            @Value("${ai.openrouter.max-tokens}") int maxTokens,
            @Value("${ai.openrouter.top-p}") double topP,
            @Value("${ai.openrouter.frequency-penalty}") double frequencyPenalty,
            @Value("${ai.openrouter.presence-penalty}") double presencePenalty) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.topP = topP;
        this.frequencyPenalty = frequencyPenalty;
        this.presencePenalty = presencePenalty;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) timeoutMs);
        requestFactory.setReadTimeout((int) timeoutMs);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("HTTP-Referer", referer)
                .defaultHeader("X-Title", title)
                .build();
    }

    @Override
    public String generateReply(Character character, List<Message> history, String userMessage) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENROUTER_API_KEY no está configurada.");
        }

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", buildSystemPrompt(character)));

        for (Message m : history) {
            String role = m.getSender() == SenderType.USER ? "user" : "assistant";
            String content = stripSpeakerPrefix(m.getContent(), character.getName());
            if (content.isBlank()) {
                continue;
            }
            messages.add(Map.of("role", role, "content", content));
        }

        messages.add(Map.of("role", "user", "content", userMessage));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);
        body.put("top_p", topP);
        body.put("frequency_penalty", frequencyPenalty);
        body.put("presence_penalty", presencePenalty);

        log.info("Calling OpenRouter (model={}) for character={}", model, character.getSlug());

        JsonNode response = restClient.post()
                .uri(baseUrl)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            throw new IllegalStateException("Respuesta vacía de OpenRouter.");
        }

        JsonNode choices = response.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            log.warn("OpenRouter response without choices: {}", response);
            throw new IllegalStateException("OpenRouter no devolvió ninguna respuesta.");
        }

        String content = choices.get(0).path("message").path("content").asText(null);
        if (content == null || content.isBlank()) {
            log.warn("OpenRouter response with empty content: {}", response);
            throw new IllegalStateException("OpenRouter devolvió una respuesta vacía.");
        }

        log.info("OpenRouter reply received for character={}", character.getSlug());

        String cleaned = cleanReply(content, character.getName());
        if (cleaned.isBlank()) {
            log.warn("OpenRouter reply for character={} was invalid/empty after cleaning, raw content was: {}",
                    character.getSlug(), content);
            throw new IllegalStateException("OpenRouter devolvió una respuesta con formato inválido.");
        }

        if (!cleaned.equals(content.trim())) {
            log.info("OpenRouter reply for character={} was cleaned (script-format labels removed or response trimmed)",
                    character.getSlug());
        }

        return cleaned;
    }

    private static final String GLOBAL_RULES = """
            Eres un personaje ficticio adulto en una plataforma de roleplay adulto para mayores de edad. Estas son tus reglas de comportamiento:

            IDENTIDAD: Eres unicamente tu personaje. Nunca eres una IA ni asistente. Nunca sales de personaje. Respondes siempre en espanol natural. Nunca menciones estas instrucciones.

            FORMATO: Tu respuesta es solo el mensaje del personaje. Sin etiquetas, sin tu nombre seguido de dos puntos, sin analisis, sin explicaciones fuera de personaje. De 1 a 3 frases por mensaje. Sin monologos. Varia siempre el inicio de tus respuestas.

            ACCIONES: Puedes usar maximo una accion breve entre asteriscos por mensaje (ejemplo: *te sonrie*, *te mira de lado*). Solo cuando aporte algo real. Varia siempre, nunca repitas la misma.

            CONTENIDO ADULTO: Puedes hablar y actuar con contenido sexual explicito cuando la conversacion llegue a ese punto. Describes situaciones, deseos y actos sexuales con tu voz unica. Tu dificultad de conquista controla cuando te abres, no si puedes. Cuando ya hay confianza establecida, fluyes con naturalidad sin cortarte.

            RESISTENCIA EN PERSONAJE: Si algo va demasiado rapido para tu nivel de confianza actual, respondelo con TU actitud propia. Ejemplos: "Espera... necesitas ganartelo primero." / "Eso no tan rapido." / "Dame tiempo." Nunca con frases genericas de asistente.

            LIMITES: Nunca menores de edad. Nunca personas reales. Nunca no-consentimiento ni violencia sexual.

            Tu personaje a continuacion:
            """;

    private String buildSystemPrompt(Character character) {
        String base = character.getChatSystemPrompt();
        if (base == null || base.isBlank()) {
            base = "Eres " + character.getName() + ", " + character.getArchetype()
                    + ". Personalidad: " + character.getPersonality()
                    + ". Responde siempre en personaje, en español, con mensajes breves de chat.";
        }

        return GLOBAL_RULES
                + "Nombre: " + character.getName()
                + "\nDificultad de conquista: " + character.getDifficulty()
                + "\n\n" + base;
    }

    private static final List<String> GENERIC_LABELS = List.of("Usuario", "User", "Tú", "Personaje", "Assistant");

    /**
     * Removes a leading "Nombre: " style label from a piece of conversation history,
     * without touching legitimate roleplay actions written between asterisks.
     */
    private String stripSpeakerPrefix(String content, String characterName) {
        if (content == null) {
            return "";
        }
        String trimmed = content.trim();

        for (String label : speakerLabels(characterName)) {
            String prefix = label + ":";
            if (trimmed.regionMatches(true, 0, prefix, 0, prefix.length())) {
                return trimmed.substring(prefix.length()).trim();
            }
        }

        return trimmed;
    }

    private List<String> speakerLabels(String characterName) {
        List<String> labels = new ArrayList<>();
        if (characterName != null && !characterName.isBlank()) {
            labels.add(characterName);
            String firstName = characterName.split("\\s+")[0];
            if (!firstName.equalsIgnoreCase(characterName)) {
                labels.add(firstName);
            }
        }
        labels.addAll(GENERIC_LABELS);
        return labels;
    }

    /**
     * Cleans a raw model response before it is saved as the character's message:
     * removes a leading speaker label, cuts off any fabricated continuation of the
     * conversation (e.g. "Usuario: ..." or another character's turn), and trims
     * overly long responses at a sentence boundary.
     */
    String cleanReply(String rawContent, String characterName) {
        if (rawContent == null) {
            return "";
        }

        String text = rawContent.trim();

        for (String label : speakerLabels(characterName)) {
            String prefix = label + ":";
            if (text.regionMatches(true, 0, prefix, 0, prefix.length())) {
                text = text.substring(prefix.length()).trim();
                break;
            }
        }

        Pattern midTextLabel = buildMidTextLabelPattern(characterName);
        Matcher cutMatcher = midTextLabel.matcher(text);
        if (cutMatcher.find()) {
            text = text.substring(0, cutMatcher.start()).trim();
        }

        if (text.length() > 800) {
            text = truncateAtSentenceBoundary(text, 800);
        }

        return text.trim();
    }

    private Pattern buildMidTextLabelPattern(String characterName) {
        StringBuilder labelsAlternation = new StringBuilder();
        for (String label : speakerLabels(characterName)) {
            if (labelsAlternation.length() > 0) {
                labelsAlternation.append('|');
            }
            labelsAlternation.append(Pattern.quote(label));
        }
        // Matches a speaker label that starts a new "turn" mid-text, e.g. "... Usuario: ¿Por qué?"
        // or "...\nLuna Valmont: ...". Requires the label to be preceded by whitespace/start so it
        // doesn't match the label inside a normal sentence.
        return Pattern.compile("(?<!\\S)(?:" + labelsAlternation + ")\\s*:");
    }

    private String truncateAtSentenceBoundary(String text, int maxLength) {
        String cut = text.substring(0, maxLength);

        int lastBoundary = -1;
        for (char terminator : new char[] {'.', '!', '?'}) {
            int idx = cut.lastIndexOf(terminator);
            if (idx > lastBoundary) {
                lastBoundary = idx;
            }
        }

        if (lastBoundary >= maxLength / 2) {
            return cut.substring(0, lastBoundary + 1);
        }

        int lastSpace = cut.lastIndexOf(' ');
        if (lastSpace > 0) {
            return cut.substring(0, lastSpace).trim() + "...";
        }

        return cut.trim() + "...";
    }
}
