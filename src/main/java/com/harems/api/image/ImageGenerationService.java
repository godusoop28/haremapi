package com.harems.api.image;

import com.harems.api.character.Character;
import com.harems.api.character.CharacterService;
import com.harems.api.common.exception.CharacterAccessDeniedException;
import com.harems.api.common.exception.InsufficientCreditsException;
import com.harems.api.image.dto.ImageGenerationRequest;
import com.harems.api.image.dto.ImageGenerationResponse;
import com.harems.api.profile.Profile;
import com.harems.api.profile.ProfileRepository;
import com.harems.api.profile.ProfileService;
import com.harems.api.subscription.PlanType;
import com.harems.api.usage.AccessControlService;
import com.harems.api.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ImageGenerationService {

    private final CharacterService characterService;
    private final ProfileService profileService;
    private final ProfileRepository profileRepository;
    private final AccessControlService accessControlService;
    private final ImageGenerationProvider imageGenerationProvider;
    private final ImageGenerationRepository imageGenerationRepository;

    @Transactional
    public ImageGenerationResponse generate(User user, ImageGenerationRequest request) {
        Profile profile = profileService.getProfile(user);
        PlanType effectivePlan = profileService.resolveEffectivePlan(profile);

        if (effectivePlan == PlanType.FREE) {
            throw new CharacterAccessDeniedException(
                    "La generación de imágenes está disponible solo para usuarios Premium o VIP.");
        }

        Character character = characterService.getCharacterEntityBySlug(request.characterSlug());
        accessControlService.checkCharacterAccess(profile, character);

        if (!character.isImageGenerationEnabled()) {
            throw new CharacterAccessDeniedException(
                    "Este personaje no tiene generación de imágenes habilitada.");
        }

        if (profile.getImageCredits() == null || profile.getImageCredits() <= 0) {
            throw new InsufficientCreditsException("No tienes créditos de imagen disponibles.");
        }

        profile.setImageCredits(profile.getImageCredits() - 1);
        profileRepository.save(profile);

        String prompt = character.getImagePromptBase() + " | tipo: " + request.type();
        String imageUrl = imageGenerationProvider.generateImage(character, prompt);

        ImageGeneration generation = ImageGeneration.builder()
                .user(user)
                .character(character)
                .prompt(prompt)
                .imageUrl(imageUrl)
                .status(ImageStatus.COMPLETED)
                .build();
        imageGenerationRepository.save(generation);

        return new ImageGenerationResponse(imageUrl, profile.getImageCredits(), ImageStatus.COMPLETED.name());
    }
}
