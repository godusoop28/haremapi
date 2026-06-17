package com.harems.api.image;

import com.harems.api.character.Character;
import com.harems.api.character.CharacterService;
import com.harems.api.common.exception.CharacterAccessDeniedException;
import com.harems.api.common.exception.InsufficientCreditsException;
import com.harems.api.common.exception.PromptBlockedException;
import com.harems.api.image.dto.ImageGenerationRequest;
import com.harems.api.image.dto.ImageGenerationResponse;
import com.harems.api.profile.Profile;
import com.harems.api.profile.ProfileRepository;
import com.harems.api.profile.ProfileService;
import com.harems.api.subscription.PlanType;
import com.harems.api.usage.AccessControlService;
import com.harems.api.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

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

    public ImageGenerationResponse generate(User user, ImageGenerationRequest request) {
        // 1. Validate plan
        Profile profile = profileService.getProfile(user);
        PlanType effectivePlan = profileService.resolveEffectivePlan(profile);

        if (effectivePlan == PlanType.FREE) {
            throw new CharacterAccessDeniedException(
                    "La generación de imágenes está disponible solo para usuarios Premium o VIP.");
        }

        // 2. Validate character
        Character character = characterService.getCharacterEntityBySlug(request.characterSlug());
        accessControlService.checkCharacterAccess(profile, character);

        if (!character.isImageGenerationEnabled()) {
            throw new CharacterAccessDeniedException(
                    "Este personaje no tiene generación de imágenes habilitada.");
        }

        // 3. Check credits
        if (profile.getImageCredits() == null || profile.getImageCredits() <= 0) {
            throw new InsufficientCreditsException("No tienes créditos de imagen disponibles.");
        }

        // 4. Moderate user prompt before doing anything else
        String userPrompt = request.userPrompt();
        if (userPrompt != null && !userPrompt.isBlank() && moderationService.isBlocked(userPrompt)) {
            log.warn("Image prompt blocked for userId={} characterSlug={}", user.getId(), request.characterSlug());
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
                    "No se puede generar ese tipo de imagen. Solo se permiten imágenes ficticias de personajes adultos.");
        }

        // 5. Build final prompt
        ImageGenerationInput input = promptBuilder.build(character, request);

        log.info("Generating image for userId={} characterSlug={} provider={}",
                user.getId(), request.characterSlug(), imageGenerationProvider.providerName());

        // 6. Deduct credit (before calling provider to prevent race conditions; refunded on failure)
        profile.setImageCredits(profile.getImageCredits() - 1);
        profileRepository.save(profile);

        // 7. Save PENDING record
        ImageGeneration generation = imageGenerationRepository.save(ImageGeneration.builder()
                .user(user)
                .character(character)
                .userPrompt(userPrompt)
                .prompt(input.positivePrompt())
                .provider(imageGenerationProvider.providerName())
                .status(ImageStatus.PENDING)
                .creditsCost(1)
                .build());

        // 8. Call provider
        try {
            log.info("Calling image provider for userId={} characterSlug={}", user.getId(), request.characterSlug());
            ImageGenerationResult result = imageGenerationProvider.generate(input);

            generation.setStatus(ImageStatus.COMPLETED);
            generation.setImageUrl(result.imageUrl());
            generation.setProviderJobId(result.providerJobId());
            generation.setCompletedAt(LocalDateTime.now());
            imageGenerationRepository.save(generation);

            log.info("Image generation completed for userId={} characterSlug={} jobId={}",
                    user.getId(), request.characterSlug(), result.providerJobId());

            return new ImageGenerationResponse(
                    generation.getId(),
                    result.imageUrl(),
                    request.characterSlug(),
                    ImageStatus.COMPLETED.name(),
                    profile.getImageCredits()
            );

        } catch (Exception e) {
            log.error("Image generation failed for userId={} characterSlug={}: {}",
                    user.getId(), request.characterSlug(), e.getMessage());

            // Refund credit
            profile.setImageCredits(profile.getImageCredits() + 1);
            profileRepository.save(profile);

            generation.setStatus(ImageStatus.FAILED);
            generation.setErrorMessage(e.getMessage());
            imageGenerationRepository.save(generation);

            throw new RuntimeException(
                    "No se pudo generar la imagen. Por favor, intenta de nuevo más tarde.");
        }
    }
}
