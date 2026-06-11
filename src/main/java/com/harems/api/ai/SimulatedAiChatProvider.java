package com.harems.api.ai;

import com.harems.api.character.Character;
import com.harems.api.message.Message;
import com.harems.api.message.SimulatedAiService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fallback provider that returns canned, personality-based replies without calling any external API.
 */
@Component
@RequiredArgsConstructor
public class SimulatedAiChatProvider implements AiChatProvider {

    private final SimulatedAiService simulatedAiService;

    @Override
    public String generateReply(Character character, List<Message> history, String userMessage) {
        return simulatedAiService.generateReply(character, userMessage);
    }
}
