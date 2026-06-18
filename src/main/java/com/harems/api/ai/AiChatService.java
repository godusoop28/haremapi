package com.harems.api.ai;

import com.harems.api.character.Character;
import com.harems.api.message.Message;
import com.harems.api.message.SimulatedAiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * Orchestrates AI chat with a primary model, a configurable fallback chain,
 * and a varied local fallback as last resort.
 *
 * Flow:
 *   1. Try OPENROUTER_MODEL (primary).
 *   2. On any error (404, 429, 5xx, timeout, invalid response) → try each fallback model
 *      from OPENROUTER_FALLBACK_MODELS in order.
 *   3. If all remote models fail → use varied local fallback (never repeats same phrase).
 *
 * Never exposes API keys in logs.
 */
@Slf4j
@Service
public class AiChatService {

    private final String provider;
    private final String primaryModel;
    private final List<String> fallbackModels;
    private final OpenRouterChatProvider openRouterChatProvider;
    private final SimulatedAiService simulatedAiService;

    public AiChatService(
            @Value("${ai.provider}") String provider,
            @Value("${ai.openrouter.model}") String primaryModel,
            @Value("${ai.openrouter.fallback-models:}") String fallbackModelsRaw,
            OpenRouterChatProvider openRouterChatProvider,
            SimulatedAiService simulatedAiService
    ) {
        this.provider = provider;
        this.primaryModel = primaryModel;
        this.openRouterChatProvider = openRouterChatProvider;
        this.simulatedAiService = simulatedAiService;

        this.fallbackModels = Arrays.stream(fallbackModelsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

        if (!fallbackModels.isEmpty()) {
            log.info("OpenRouter fallback chain: {} → {}", primaryModel, fallbackModels);
        }
    }

    /**
     * Returns a reply. Never throws — uses local fallback as last resort.
     */
    public String generateReply(Character character, List<Message> history, String userMessage) {
        if (!"OPENROUTER".equalsIgnoreCase(provider)) {
            log.debug("AI_PROVIDER={} — SimulatedAi for character={}", provider, character.getSlug());
            return simulatedAiService.generateFallback(character);
        }

        // ── 1. Try primary model ───────────────────────────────────────────────
        try {
            log.info("Trying primary model={} for character={}", primaryModel, character.getSlug());
            String reply = openRouterChatProvider.generateReply(character, history, userMessage, primaryModel);
            log.info("Primary model={} replied for character={}", primaryModel, character.getSlug());
            return reply;
        } catch (Exception e) {
            log.warn("Primary model={} failed for character={}: {}", primaryModel, character.getSlug(), summarize(e));
        }

        // ── 2. Try fallback models in order ───────────────────────────────────
        for (String fallback : fallbackModels) {
            try {
                log.info("Trying fallback model={} for character={}", fallback, character.getSlug());
                String reply = openRouterChatProvider.generateReply(character, history, userMessage, fallback);
                log.info("Fallback model={} replied for character={}", fallback, character.getSlug());
                return reply;
            } catch (Exception e) {
                log.warn("Fallback model={} failed for character={}: {}", fallback, character.getSlug(), summarize(e));
            }
        }

        // ── 3. All models failed — local varied fallback ───────────────────────
        log.warn("All OpenRouter models failed for character={}. Using local varied fallback.", character.getSlug());
        return simulatedAiService.generateFallback(character);
    }

    /** Summarizes an exception without leaking API keys or secrets. */
    private String summarize(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return e.getClass().getSimpleName();
        // Trim to avoid logging huge response bodies
        return msg.length() > 200 ? msg.substring(0, 200) + "..." : msg;
    }
}
