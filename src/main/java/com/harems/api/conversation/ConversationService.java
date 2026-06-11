package com.harems.api.conversation;

import com.harems.api.common.exception.ResourceNotFoundException;
import com.harems.api.conversation.dto.ConversationResponse;
import com.harems.api.message.Message;
import com.harems.api.message.MessageRepository;
import com.harems.api.message.dto.MessageResponse;
import com.harems.api.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    @Transactional(readOnly = true)
    public List<ConversationResponse> getUserConversations(User user) {
        return conversationRepository.findByUserOrderByUpdatedAtDesc(user).stream()
                .map(conversation -> toResponse(conversation, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversationResponse getConversation(User user, Long conversationId) {
        Conversation conversation = conversationRepository.findByIdAndUser(conversationId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Conversación no encontrada."));

        return toResponse(conversation, true);
    }

    private ConversationResponse toResponse(Conversation conversation, boolean includeMessages) {
        List<MessageResponse> messages = includeMessages
                ? messageRepository.findByConversationOrderByCreatedAtAsc(conversation).stream()
                    .map(this::toMessageResponse)
                    .toList()
                : List.of();

        return new ConversationResponse(
                conversation.getId(),
                conversation.getCharacter().getSlug(),
                conversation.getCharacter().getName(),
                conversation.getCharacter().getImageUrl(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt(),
                messages
        );
    }

    private MessageResponse toMessageResponse(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getSender(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
