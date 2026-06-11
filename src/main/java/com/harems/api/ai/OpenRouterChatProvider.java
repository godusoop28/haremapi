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
import java.util.List;
import java.util.Map;

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

    public OpenRouterChatProvider(
            @Value("${ai.openrouter.api-key}") String apiKey,
            @Value("${ai.openrouter.model}") String model,
            @Value("${ai.openrouter.base-url}") String baseUrl,
            @Value("${ai.openrouter.referer}") String referer,
            @Value("${ai.openrouter.title}") String title,
            @Value("${ai.openrouter.timeout-ms}") long timeoutMs) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;

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
            messages.add(Map.of("role", role, "content", m.getContent()));
        }

        messages.add(Map.of("role", "user", "content", userMessage));

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", messages
        );

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
        return content.trim();
    }

    private String buildSystemPrompt(Character character) {
        String base = character.getChatSystemPrompt();
        if (base != null && !base.isBlank()) {
            return base;
        }
        return "Eres " + character.getName() + ", " + character.getArchetype()
                + ". Personalidad: " + character.getPersonality()
                + ". Responde siempre en personaje, en español, con mensajes breves de chat.";
    }
}
