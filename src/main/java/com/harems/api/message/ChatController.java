package com.harems.api.message;

import com.harems.api.message.dto.ChatRequest;
import com.harems.api.message.dto.ChatResponse;
import com.harems.api.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/send")
    public ChatResponse send(@AuthenticationPrincipal UserPrincipal principal,
                              @Valid @RequestBody ChatRequest request) {
        return chatService.sendMessage(principal.getUser(), request);
    }
}
