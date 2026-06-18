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
 * Orquesta el chat con OpenRouter mediante una cadena de fallback:
 *
 *   1. Intenta OPENROUTER_MODEL (modelo principal).
 *      — Si detecta rechazo inapropiado ante contenido adulto, hace retry interno con prompt reforzado.
 *      — Si el retry también falla, lanza excepción ("BAD_ADULT_REFUSAL") para pasar al siguiente modelo.
 *   2. Intenta cada modelo de OPENROUTER_FALLBACK_MODELS en orden.
 *   3. Si todos fallan → fallback local variado en personaje.
 *      — Si el mensaje era adulto permitido → fallback local adulto en personaje.
 *      — Si era mensaje normal → fallback local neutro.
 *
 * Nunca expone API keys en logs.
 */
@Slf4j
@Service
public class AiChatService {

    private final String provider;
    private final String primaryModel;
    private final List<String> fallbackModels;
    private final OpenRouterChatProvider openRouterChatProvider;
    private final SimulatedAiService simulatedAiService;
    private final ChatModerationService moderation;

    public AiChatService(
            @Value("${ai.provider}") String provider,
            @Value("${ai.openrouter.model}") String primaryModel,
            @Value("${ai.openrouter.fallback-models:}") String fallbackModelsRaw,
            OpenRouterChatProvider openRouterChatProvider,
            SimulatedAiService simulatedAiService,
            ChatModerationService moderation
    ) {
        this.provider = provider;
        this.primaryModel = primaryModel;
        this.openRouterChatProvider = openRouterChatProvider;
        this.simulatedAiService = simulatedAiService;
        this.moderation = moderation;

        this.fallbackModels = Arrays.stream(fallbackModelsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

        log.info("AiChatService init: provider={} primary={} fallbacks={} adultMode={} allowVulgar={}",
                provider, primaryModel, fallbackModels,
                moderation.isAdultModeEnabled(), moderation.isVulgarAllowed());
    }

    /**
     * Genera respuesta. Nunca lanza excepción — usa fallback local como último recurso.
     */
    public String generateReply(Character character, List<Message> history, String userMessage) {
        if (!"OPENROUTER".equalsIgnoreCase(provider)) {
            log.debug("AI_PROVIDER={} — usando SimulatedAi para character={}", provider, character.getSlug());
            return simulatedAiService.generateFallback(character);
        }

        boolean isAdultRequest = moderation.isAdultContent(userMessage);
        boolean isIllegal = moderation.isIllegalOrUnsafe(userMessage);

        log.info("generateReply — character={} adultMode={} isAdult={} isIllegal={}",
                character.getSlug(), moderation.isAdultModeEnabled(), isAdultRequest, isIllegal);

        // Bloquear mensajes ilegales antes de llamar a la IA
        if (isIllegal) {
            log.warn("Message blocked by moderation for character={}", character.getSlug());
            return "Lo que pides no está permitido en esta plataforma.";
        }

        // ── 1. Modelo principal ────────────────────────────────────────────────
        try {
            log.info("Trying primary model={} for character={}", primaryModel, character.getSlug());
            String reply = openRouterChatProvider.generateReply(character, history, userMessage, primaryModel);
            log.info("Primary model={} replied OK (source: primary) for character={}", primaryModel, character.getSlug());
            return reply;
        } catch (Exception e) {
            boolean wasBadRefusal = e.getMessage() != null && e.getMessage().startsWith("BAD_ADULT_REFUSAL");
            log.warn("Primary model={} failed for character={} wasBadRefusal={}: {}",
                    primaryModel, character.getSlug(), wasBadRefusal, summarize(e));
        }

        // ── 2. Modelos fallback en orden ──────────────────────────────────────
        for (String fallback : fallbackModels) {
            try {
                log.info("Trying fallback model={} for character={}", fallback, character.getSlug());
                String reply = openRouterChatProvider.generateReply(character, history, userMessage, fallback);
                log.info("Fallback model={} replied OK (source: fallback) for character={}", fallback, character.getSlug());
                return reply;
            } catch (Exception e) {
                log.warn("Fallback model={} failed for character={}: {}", fallback, character.getSlug(), summarize(e));
            }
        }

        // ── 3. Todos los modelos fallaron — fallback local ─────────────────────
        log.warn("All remote models failed for character={}. Using local fallback (source: local) isAdult={}",
                character.getSlug(), isAdultRequest);

        if (isAdultRequest && moderation.isAdultModeEnabled()) {
            return simulatedAiService.generateAdultFallback(character);
        }
        return simulatedAiService.generateFallback(character);
    }

    private String summarize(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return e.getClass().getSimpleName();
        return msg.length() > 200 ? msg.substring(0, 200) + "..." : msg;
    }
}
