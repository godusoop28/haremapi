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
            log.debug("AI_PROVIDER={} — SimulatedAi for character={}", provider, character.getSlug());
            return simulatedAiChatProvider.generateReply(character, history, userMessage);
        }

        log.info("OpenRouter model={} character={}", model, character.getSlug());
        // Propaga la excepción — ChatService tiene el catch y el fallback en personaje.
        // Nunca provoca rollback porque ChatService captura antes de que se propague.
        return openRouterChatProvider.generateReply(character, history, userMessage);
    }
}
