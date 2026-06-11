package com.harems.api.ai;

import com.harems.api.character.Character;
import com.harems.api.message.Message;

import java.util.List;

/**
 * Generates a chat reply for a character given the recent conversation history.
 */
public interface AiChatProvider {

    /**
     * @param character the character the user is chatting with
     * @param history   recent messages in the conversation, ordered oldest to newest,
     *                   not including {@code userMessage}
     * @param userMessage the latest message sent by the user
     * @return the generated reply text
     */
    String generateReply(Character character, List<Message> history, String userMessage);
}
