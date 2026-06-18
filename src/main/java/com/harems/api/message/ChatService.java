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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Slf4j
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

        // Obtener o crear conversación
        Conversation conversation = conversationRepository.findByUserAndCharacter(user, character)
                .orElseGet(() -> conversationRepository.save(
                        Conversation.builder()
                                .user(user)
                                .character(character)
                                .build()));

        // Historial reciente para contexto de la IA
        List<Message> history = messageRepository
                .findByConversationOrderByCreatedAtDesc(conversation, PageRequest.of(0, historySize));
        Collections.reverse(history);

        // ── Guardar mensaje del usuario (SIEMPRE, sin importar lo que pase después) ──
        messageRepository.save(Message.builder()
                .conversation(conversation)
                .sender(SenderType.USER)
                .content(request.message())
                .build());

        // ── Generar reply de IA con fallback en personaje ──────────────────────────
        // IMPORTANTE: el error de IA se captura aquí para que NUNCA provoque
        // un rollback de la transacción. El mensaje del usuario siempre queda guardado.
        String reply;
        try {
            reply = aiChatService.generateReply(character, history, request.message());
        } catch (Exception e) {
            log.error("AI reply failed for character={} userId={}: {}",
                    character.getSlug(), user.getId(), e.getMessage());
            reply = fallbackReply(character);
        }

        // ── Guardar respuesta de la IA (SIEMPRE) ────────────────────────────────────
        messageRepository.save(Message.builder()
                .conversation(conversation)
                .sender(SenderType.AI)
                .content(reply)
                .build());

        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        Integer messagesLimit = accessControlService.getFreeMessagesLimit(profile);
        return new ChatResponse(conversation.getId(), reply, messagesUsed, messagesLimit);
    }

    /**
     * Respuesta de fallback en personaje para cuando la IA falla.
     * Breve, natural, no rompe la inmersión.
     */
    private String fallbackReply(Character character) {
        return switch (character.getSlug()) {
            case "luna-valmont"    -> "Uy, perdona... se me fue el hilo. ¿Qué me decías?";
            case "hana-mori"       -> "Oye, me lagueé un segundo jaja. ¿Qué dijiste?";
            case "aurora-sterling" -> "Disculpa la interrupción. Continúa.";
            case "valeria-cruz"    -> "Hey, me desconecté un momento. ¿Me repetías?";
            case "camila-rios"     -> "Perdona, estaba en otra cosa. ¿Qué decías?";
            case "kiara-blake"     -> "Lag de conexión xd. Repite, ¿qué fue?";
            case "isabella-laurent"-> "Disculpa. ¿Podrías repetirlo?";
            case "nara-voss"       -> "... me perdí. Repite.";
            case "sasha-monroe"    -> "Sorry, me fui un seg. ¿Qué dijiste?";
            case "mei-tanaka"      -> "Ah... perdona, ¿qué ibas a decir?";
            case "renata-soler"    -> "Me fui un momento. ¿Qué ibas a decir?";
            case "victoria-hale"   -> "Perdona la distracción. Continúa.";
            default                -> "Perdona, ¿puedes repetir eso?";
        };
    }
}
