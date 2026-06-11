package com.harems.api.message;

import com.harems.api.ai.AiChatService;
import com.harems.api.character.Character;
import com.harems.api.character.CharacterService;
import com.harems.api.conversation.Conversation;
import com.harems.api.conversation.ConversationRepository;
import com.harems.api.message.dto.ChatRequest;
import com.harems.api.message.dto.ChatResponse;
import com.harems.api.profile.Profile;
import com.harems.api.profile.ProfileService;
import com.harems.api.usage.AccessControlService;
import com.harems.api.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final CharacterService characterService;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ProfileService profileService;
    private final AccessControlService accessControlService;
    private final AiChatService aiChatService;

    @Value("${ai.openrouter.history-size}")
    private int historySize;

    @Transactional
    public ChatResponse sendMessage(User user, ChatRequest request) {
        Character character = characterService.getCharacterEntityBySlug(request.characterSlug());
        Profile profile = profileService.getProfile(user);

        accessControlService.checkCharacterAccess(profile, character);
        int messagesUsed = accessControlService.checkAndRegisterMessageUsage(user, profile, character);

        Conversation conversation = conversationRepository.findByUserAndCharacter(user, character)
                .orElseGet(() -> conversationRepository.save(
                        Conversation.builder()
                                .user(user)
                                .character(character)
                                .build()));

        List<Message> history = messageRepository
                .findByConversationOrderByCreatedAtDesc(conversation, PageRequest.of(0, historySize));
        Collections.reverse(history);

        messageRepository.save(Message.builder()
                .conversation(conversation)
                .sender(SenderType.USER)
                .content(request.message())
                .build());

        String reply = aiChatService.generateReply(character, history, request.message());

        messageRepository.save(Message.builder()
                .conversation(conversation)
                .sender(SenderType.AI)
                .content(reply)
                .build());

        conversation.setUpdatedAt(java.time.LocalDateTime.now());
        conversationRepository.save(conversation);

        Integer messagesLimit = accessControlService.getFreeMessagesLimit(profile);

        return new ChatResponse(conversation.getId(), reply, messagesUsed, messagesLimit);
    }
}
