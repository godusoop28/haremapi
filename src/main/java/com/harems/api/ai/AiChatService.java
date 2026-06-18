package com.harems.api.ai;

import com.harems.api.character.Character;
import com.harems.api.message.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class AiChatService {

    private final String provider;
    private final String model;
    private final OpenRouterChatProvider openRouterChatProvider;
    private final SimulatedAiChatProvider simulatedAiChatProvider;

    public AiChatService(
            @Value("${ai.provider}") String provider,
            @Value("${ai.openrouter.model}") String model,
            OpenRouterChatProvider openRouterChatProvider,
            SimulatedAiChatProvider simulatedAiChatProvider) {
        this.provider = provider;
        this.model = model;
        this.openRouterChatProvider = openRouterChatProvider;
        this.simulatedAiChatProvider = simulatedAiChatProvider;
    }

    public String generateReply(Character character, List<Message> history, String userMessage) {
        if (!"OPENROUTER".equalsIgnoreCase(provider)) {
            log.info("AI_PROVIDER={} — using SimulatedAiService for character={}", provider, character.getSlug());
            return simulatedAiChatProvider.generateReply(character, history, userMessage);
        }

        log.info("Calling OpenRouter model={} character={}", model, character.getSlug());
        // Si OpenRouter falla, propagamos el error en vez de caer a respuestas enlatadas.
        // Esto hace que el error sea visible en el frontend en vez de silencioso.
        return openRouterChatProvider.generateReply(character, history, userMessage);
    }
}
