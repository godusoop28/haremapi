package com.harems.api.message;

import com.harems.api.conversation.Conversation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationOrderByCreatedAtAsc(Conversation conversation);

    List<Message> findByConversationOrderByCreatedAtDesc(Conversation conversation, Pageable pageable);
}
