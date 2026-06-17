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

    private static final int CONTEXT_MESSAGES = 6;

    public ImageGenerationResponse generate(User user, ImageGenerationRequest request) {
        log.info("Image generation requested — userId={} characterSlug={} provider={} adultLevel={}",
                user.getId(), request.characterSlug(),
                imageGenerationProvider.providerName(), request.adultLevel());

        // 1. Validate plan
        Profile profile = profileService.getProfile(user);
        PlanType effectivePlan = profileService.resolveEffectivePlan(profile);

        if (effectivePlan == PlanType.FREE) {
            log.warn("Image generation blocked by plan — userId={}", user.getId());
            throw new CharacterAccessDeniedException(
                    "La generación de imágenes está disponible solo para usuarios Premium o VIP.");
        }

        // 2. Validate character
        Character character = characterService.getCharacterEntityBySlug(request.characterSlug());
        accessControlService.checkCharacterAccess(profile, character);

        if (!character.isImageGenerationEnabled()) {
            log.warn("Image generation blocked — imageGenerationEnabled=false slug={}", request.characterSlug());
            throw new CharacterAccessDeniedException(
                    "Este personaje no tiene generación de imágenes habilitada.");
        }

        // 3. Check credits
        if (profile.getImageCredits() == null || profile.getImageCredits() <= 0) {
            log.warn("Image generation blocked — no credits userId={}", user.getId());
            throw new InsufficientCreditsException("No tienes créditos de imagen disponibles.");
        }

        // 4. Moderate user prompt (only blocks illegal / unsafe content — NOT adult fiction)
        String userPrompt = request.userPrompt();
        if (userPrompt != null && !userPrompt.isBlank() && moderationService.isBlocked(userPrompt)) {
            log.warn("Image prompt blocked by moderation — userId={} characterSlug={}",
                    user.getId(), request.characterSlug());
            imageGenerationRepository.save(ImageGeneration.builder()
                    .user(user)
                    .character(character)
                    .userPrompt(userPrompt)
                    .prompt("BLOCKED")
                    .provider("NONE")
                    .status(ImageStatus.BLOCKED)
                    .creditsCost(0)
                    .build());
            throw new PromptBlockedException(
                    "Ese tipo de imagen no está permitido. Solo se generan imágenes ficticias adultas consensuadas.");
        }

        // 5. Fetch recent conversation context (last N messages)
        List<Message> recentMessages = fetchRecentMessages(user, character);
        log.info("Context messages fetched — userId={} characterSlug={} count={}",
                user.getId(), request.characterSlug(), recentMessages.size());

        // 6. Analyze context → determine adultLevel, mood, scene
        ImageContextAnalysis contextAnalysis = contextBuilder.analyze(
                character, recentMessages, userPrompt, request.adultLevel());

        // 7. Build contextual prompt
        ImageGenerationInput input = promptBuilder.buildWithContext(character, request, contextAnalysis);

        // 8. Deduct credit before calling provider (refunded on failure)
        profile.setImageCredits(profile.getImageCredits() - 1);
        profileRepository.save(profile);
        log.info("Credit deducted — userId={} remainingCredits={}", user.getId(), profile.getImageCredits());

        // 9. Save PENDING record
        ImageGeneration generation = imageGenerationRepository.save(ImageGeneration.builder()
                .user(user)
                .character(character)
                .userPrompt(userPrompt)
                .prompt(input.positivePrompt())
                .provider(imageGenerationProvider.providerName())
                .status(ImageStatus.PENDING)
                .creditsCost(1)
                .build());

        log.info("ImageGeneration PENDING — id={} userId={} characterSlug={} adultLevel={} provider={}",
                generation.getId(), user.getId(), request.characterSlug(),
                contextAnalysis.adultLevel(), imageGenerationProvider.providerName());

        // 10. Call provider
        try {
            ImageGenerationResult result = imageGenerationProvider.generate(input);

            generation.setStatus(ImageStatus.COMPLETED);
            generation.setImageUrl(result.imageUrl());
            generation.setProviderJobId(result.providerJobId());
            generation.setCompletedAt(LocalDateTime.now());
            imageGenerationRepository.save(generation);

            log.info("Image generation COMPLETED — id={} userId={} characterSlug={} jobId={} url={}",
                    generation.getId(), user.getId(), request.characterSlug(),
                    result.providerJobId(), result.imageUrl());

            return new ImageGenerationResponse(
                    generation.getId(),
                    result.imageUrl(),
                    request.characterSlug(),
                    ImageStatus.COMPLETED.name(),
                    profile.getImageCredits()
            );

        } catch (Exception e) {
            log.error("Image generation FAILED — id={} userId={} characterSlug={} cause={}",
                    generation.getId(), user.getId(), request.characterSlug(), e.getMessage());

            // Refund credit
            profile.setImageCredits(profile.getImageCredits() + 1);
            profileRepository.save(profile);
            log.info("Credit refunded — userId={} restoredCredits={}", user.getId(), profile.getImageCredits());

            generation.setStatus(ImageStatus.FAILED);
            generation.setErrorMessage(e.getMessage());
            imageGenerationRepository.save(generation);

            throw new RuntimeException("No se pudo generar la imagen. Inténtalo de nuevo más tarde.");
        }
    }

    private List<Message> fetchRecentMessages(User user, Character character) {
        return conversationRepository.findByUserAndCharacter(user, character)
                .map(conv -> {
                    List<Message> msgs = messageRepository.findByConversationOrderByCreatedAtDesc(
                            conv, PageRequest.of(0, CONTEXT_MESSAGES));
                    Collections.reverse(msgs); // chronological order
                    return msgs;
                })
                .orElse(List.of());
    }
}
