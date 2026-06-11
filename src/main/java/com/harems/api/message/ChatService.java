package com.harems.api.message;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final CharacterService characterService;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ProfileService profileService;
    private final AccessControlService accessControlService;
    private final SimulatedAiService simulatedAiService;

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

        messageRepository.save(Message.builder()
                .conversation(conversation)
                .sender(SenderType.USER)
                .content(request.message())
                .build());

        String reply = simulatedAiService.generateReply(character, request.message());

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
