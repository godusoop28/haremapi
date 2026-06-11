package com.harems.api.profile;

import com.harems.api.common.exception.ResourceNotFoundException;
import com.harems.api.subscription.PlanBenefits;
import com.harems.api.subscription.PlanType;
import com.harems.api.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileRepository profileRepository;

    public Profile getProfile(User user) {
        return profileRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil no encontrado."));
    }

    /**
     * Returns the plan the user should currently be treated as.
     * If a paid plan has expired, the profile is downgraded to FREE and persisted.
     */
    @Transactional
    public PlanType resolveEffectivePlan(Profile profile) {
        if (profile.getPlan() != PlanType.FREE
                && profile.getPlanExpiresAt() != null
                && profile.getPlanExpiresAt().isBefore(LocalDateTime.now())) {
            applyPlan(profile, PlanType.FREE);
        }
        return profile.getPlan();
    }

    /**
     * Applies a plan change to the profile: updates plan, expiration date and image credits.
     */
    @Transactional
    public Profile applyPlan(Profile profile, PlanType plan) {
        profile.setPlan(plan);

        Integer durationDays = PlanBenefits.durationDaysFor(plan);
        profile.setPlanExpiresAt(durationDays == null ? null : LocalDateTime.now().plusDays(durationDays));
        profile.setImageCredits(PlanBenefits.imageCreditsFor(plan));

        return profileRepository.save(profile);
    }
}
