package com.harems.api.conversation;

import com.harems.api.conversation.dto.ConversationResponse;
import com.harems.api.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping
    public List<ConversationResponse> getConversations(@AuthenticationPrincipal UserPrincipal principal) {
        return conversationService.getUserConversations(principal.getUser());
    }

    @GetMapping("/{id}")
    public ConversationResponse getConversation(@AuthenticationPrincipal UserPrincipal principal,
                                                  @PathVariable Long id) {
        return conversationService.getConversation(principal.getUser(), id);
    }
}
