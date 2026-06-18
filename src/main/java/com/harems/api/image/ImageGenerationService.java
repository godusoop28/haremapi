package com.harems.api.image;

import com.harems.api.character.Character;
import com.harems.api.character.CharacterService;
import com.harems.api.common.exception.CharacterAccessDeniedException;
import com.harems.api.common.exception.InsufficientCreditsException;
import com.harems.api.common.exception.PromptBlockedException;
import com.harems.api.conversation.ConversationRepository;
import com.harems.api.image.dto.ImageGenerationRequest;
import com.harems.api.image.dto.ImageGenerationResponse;
import com.harems.api.message.Message;
import com.harems.api.message.MessageRepository;
import com.harems.api.profile.Profile;
import com.harems.api.profile.ProfileRepository;
import com.harems.api.profile.ProfileService;
import com.harems.api.subscription.PlanType;
import com.harems.api.usage.AccessControlService;
import com.harems.api.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageGenerationService {

    private final CharacterService characterService;
    private final ProfileService profileService;
    private final ProfileRepository profileRepository;
    private final AccessControlService accessControlService;
    private final ImageGenerationProvider imageGenerationProvider;
    private final ImageGenerationRepository imageGenerationRepository;
    private final ImageModerationService moderationService;
    private final ImagePromptBuilder promptBuilder;
    private final ConversationImageContextBuilder contextBuilder;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    // 20 mensajes = ~10 intercambios de conversación — suficiente para detectar escenas mencionadas hace rato
    private static final int CONTEXT_MESSAGES = 20;

    public ImageGenerationResponse generate(User user, ImageGenerationRequest request) {
        log.info("Image generation requested — userId={} characterSlug={} provider={} adultLevel={}",
                user.getId(), request.characterSlug(),
                imageGenerationProvider.providerName(), request.adultLevel());

        // 1. Validar plan
        Profile profile = profileService.getProfile(user);
        PlanType effectivePlan = profileService.resolveEffectivePlan(profile);
        if (effectivePlan == PlanType.FREE) {
            throw new CharacterAccessDeniedException(
                    "La generación de imágenes está disponible solo para usuarios Premium o VIP.");
        }

        // 2. Validar personaje
        Character character = characterService.getCharacterEntityBySlug(request.characterSlug());
        accessControlService.checkCharacterAccess(profile, character);
        if (!character.isImageGenerationEnabled()) {
            throw new CharacterAccessDeniedException("Este personaje no tiene generación de imágenes habilitada.");
        }

        // 3. Contar mensajes totales con este personaje → determina confianza y coste
        int totalMessages  = countTotalMessages(user, character);
        int creditCost     = computeCreditCost(totalMessages, character);
        boolean highTrust  = creditCost == 1;

        log.info("Trust check — userId={} characterSlug={} totalMessages={} creditCost={} highTrust={}",
                user.getId(), request.characterSlug(), totalMessages, creditCost, highTrust);

        // 4. Validar créditos según coste real
        if (profile.getImageCredits() == null || profile.getImageCredits() < creditCost) {
            String msg = creditCost > 1
                    ? String.format("Necesitas %d créditos para generar con %s (confianza baja — chatea más con ella).",
                            creditCost, character.getName())
                    : "No tienes créditos de imagen disponibles.";
            throw new InsufficientCreditsException(msg);
        }

        // 5. Moderar prompt del usuario
        String userPrompt = request.userPrompt();
        if (userPrompt != null && !userPrompt.isBlank() && moderationService.isBlocked(userPrompt)) {
            log.warn("Image prompt blocked by moderation — userId={} characterSlug={}", user.getId(), request.characterSlug());
            imageGenerationRepository.save(ImageGeneration.builder()
                    .user(user).character(character).userPrompt(userPrompt)
                    .prompt("BLOCKED").provider("NONE").status(ImageStatus.BLOCKED).creditsCost(0)
                    .build());
            throw new PromptBlockedException(
                    "Ese tipo de imagen no está permitido. Solo se generan imágenes ficticias adultas consensuadas.");
        }

        // 6. Obtener mensajes recientes para contexto visual
        List<Message> recentMessages = fetchRecentMessages(user, character);

        // 7. Analizar contexto → determina nivel adulto, mood, escena
        ImageContextAnalysis contextAnalysis = contextBuilder.analyze(
                character, recentMessages, userPrompt, request.adultLevel(), totalMessages);

        // 8. Construir prompt contextual
        ImageGenerationInput input = promptBuilder.buildWithContext(character, request, contextAnalysis);

        // 9. Descontar créditos
        profile.setImageCredits(profile.getImageCredits() - creditCost);
        profileRepository.save(profile);
        log.info("Credits deducted — userId={} cost={} remaining={}", user.getId(), creditCost, profile.getImageCredits());

        // 10. Guardar registro PENDING
        ImageGeneration generation = imageGenerationRepository.save(ImageGeneration.builder()
                .user(user).character(character).userPrompt(userPrompt)
                .prompt(input.positivePrompt())
                .provider(imageGenerationProvider.providerName())
                .status(ImageStatus.PENDING).creditsCost(creditCost)
                .build());

        log.info("ImageGeneration PENDING — id={} userId={} characterSlug={} level={} creditCost={}",
                generation.getId(), user.getId(), request.characterSlug(),
                contextAnalysis.adultLevel(), creditCost);

        // 11. Llamar al proveedor
        try {
            ImageGenerationResult result = imageGenerationProvider.generate(input);

            generation.setStatus(ImageStatus.COMPLETED);
            generation.setImageUrl(result.imageUrl());
            generation.setProviderJobId(result.providerJobId());
            generation.setCompletedAt(LocalDateTime.now());
            imageGenerationRepository.save(generation);

            log.info("Image generation COMPLETED — id={} userId={} url={}", generation.getId(), user.getId(), result.imageUrl());

            return new ImageGenerationResponse(
                    generation.getId(),
                    result.imageUrl(),
                    request.characterSlug(),
                    ImageStatus.COMPLETED.name(),
                    profile.getImageCredits(),
                    creditCost,
                    highTrust,
                    totalMessages
            );

        } catch (Exception e) {
            log.error("Image generation FAILED — id={} userId={} cause={}", generation.getId(), user.getId(), e.getMessage());

            // Reembolsar créditos
            profile.setImageCredits(profile.getImageCredits() + creditCost);
            profileRepository.save(profile);

            generation.setStatus(ImageStatus.FAILED);
            generation.setErrorMessage(e.getMessage());
            imageGenerationRepository.save(generation);

            throw new RuntimeException("No se pudo generar la imagen. Inténtalo de nuevo más tarde.");
        }
    }

    // ── Confianza y coste ──────────────────────────────────────────────────────

    /**
     * Coste en créditos:
     *   highTrust (mensajes >= umbral) → 1 crédito
     *   lowTrust                       → 2 créditos
     */
    private int computeCreditCost(int totalMessages, Character character) {
        int threshold = trustThreshold(character);
        return totalMessages >= threshold ? 1 : 2;
    }

    /** Umbral de mensajes totales según dificultad del personaje. */
    private int trustThreshold(Character character) {
        String d = character.getDifficulty() != null ? character.getDifficulty().toLowerCase() : "";
        if (d.contains("extrema"))                       return 50;
        if (d.contains("muy alta"))                      return 30;
        if (d.contains("alta") && d.contains("media"))  return 20;
        if (d.contains("alta"))                          return 24;
        if (d.contains("fácil") && d.contains("media")) return 8;
        if (d.contains("media") && !d.contains("alta")) return 14;
        if (d.contains("fácil"))                         return 5;
        return 12;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private int countTotalMessages(User user, Character character) {
        return conversationRepository.findByUserAndCharacter(user, character)
                .map(conv -> (int) messageRepository.countByConversation(conv))
                .orElse(0);
    }

    private List<Message> fetchRecentMessages(User user, Character character) {
        return conversationRepository.findByUserAndCharacter(user, character)
                .map(conv -> {
                    List<Message> msgs = messageRepository.findByConversationOrderByCreatedAtDesc(
                            conv, PageRequest.of(0, CONTEXT_MESSAGES));
                    Collections.reverse(msgs);
                    return msgs;
                })
                .orElse(List.of());
    }
}
