package com.harems.api.ai;

import com.harems.api.character.Character;
import com.harems.api.message.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Selects the configured AI chat provider (OpenRouter or simulated) and falls back
 * to simulated replies if the real provider is disabled, misconfigured or fails.
 */
@Slf4j
@Service
public class AiChatService {

    private final String provider;
    private final OpenRouterChatProvider openRouterChatProvider;
    private final SimulatedAiChatProvider simulatedAiChatProvider;

    public AiChatService(
            @Value("${ai.provider}") String provider,
            OpenRouterChatProvider openRouterChatProvider,
            SimulatedAiChatProvider simulatedAiChatProvider) {
        this.provider = provider;
        this.openRouterChatProvider = openRouterChatProvider;
        this.simulatedAiChatProvider = simulatedAiChatProvider;
    }

    public String generateReply(Character character, List<Message> history, String userMessage) {
        if (!"OPENROUTER".equalsIgnoreCase(provider)) {
            log.info("AI_PROVIDER={} -> using SimulatedAiService for character={}", provider, character.getSlug());
            return simulatedAiChatProvider.generateReply(character, history, userMessage);
        }

        try {
            String reply = openRouterChatProvider.generateReply(character, history, userMessage);
            log.info("Reply generated via OpenRouter for character={}", character.getSlug());
            return reply;
        } catch (Exception e) {
            log.error("OpenRouter call failed for character={}, falling back to SimulatedAiService: {}",
                    character.getSlug(), e.getMessage(), e);
            return simulatedAiChatProvider.generateReply(character, history, userMessage);
        }
    }
}
