package com.harems.api.usage;

import com.harems.api.character.AccessType;
import com.harems.api.character.Character;
import com.harems.api.common.exception.CharacterAccessDeniedException;
import com.harems.api.common.exception.MessageLimitExceededException;
import com.harems.api.profile.Profile;
import com.harems.api.profile.ProfileService;
import com.harems.api.subscription.PlanType;
import com.harems.api.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AccessControlService {

    private final UsageLimitRepository usageLimitRepository;
    private final ProfileService profileService;

    @Value("${app.limits.free-messages-per-character}")
    private int freeMessagesPerCharacter;

    /**
     * Validates whether the given profile (with its effective plan) can chat with the character.
     * Throws {@link CharacterAccessDeniedException} with a user-facing message if access is denied.
     */
    public void checkCharacterAccess(Profile profile, Character character) {
        PlanType effectivePlan = profileService.resolveEffectivePlan(profile);

        if (character.getAccessType() == AccessType.VIP) {
            if (effectivePlan != PlanType.VIP) {
                throw new CharacterAccessDeniedException("Este personaje está disponible en VIP.");
            }
            return;
        }

        if (character.getAccessType() == AccessType.PREMIUM) {
            boolean hasPremiumAccess = effectivePlan == PlanType.TRIAL_3_DAYS
                    || effectivePlan == PlanType.PREMIUM
                    || effectivePlan == PlanType.VIP;
            if (!hasPremiumAccess) {
                throw new CharacterAccessDeniedException("Este personaje está disponible en Premium.");
            }
        }
    }

    /**
     * Checks the free message limit (if applicable) and increments the usage counters.
     *
     * @return the number of messages used by the user with this character so far (after incrementing).
     */
    @Transactional
    public int checkAndRegisterMessageUsage(User user, Profile profile, Character character) {
        PlanType effectivePlan = profileService.resolveEffectivePlan(profile);

        UsageLimit usageLimit = usageLimitRepository.findByUserAndCharacter(user, character)
                .orElseGet(() -> UsageLimit.builder()
                        .user(user)
                        .character(character)
                        .messagesUsed(0)
                        .imagesUsed(0)
                        .periodStart(LocalDateTime.now())
                        .build());

        if (effectivePlan == PlanType.FREE && usageLimit.getMessagesUsed() >= freeMessagesPerCharacter) {
            throw new MessageLimitExceededException(
                    "Has alcanzado el límite de " + freeMessagesPerCharacter
                            + " mensajes gratuitos. Mejora tu plan para continuar.");
        }

        usageLimit.setMessagesUsed(usageLimit.getMessagesUsed() + 1);
        usageLimitRepository.save(usageLimit);

        profile.setMessagesUsed(profile.getMessagesUsed() + 1);

        return usageLimit.getMessagesUsed();
    }

    public Integer getFreeMessagesLimit(Profile profile) {
        PlanType effectivePlan = profileService.resolveEffectivePlan(profile);
        return effectivePlan == PlanType.FREE ? freeMessagesPerCharacter : null;
    }
}
