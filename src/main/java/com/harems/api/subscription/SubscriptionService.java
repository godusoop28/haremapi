package com.harems.api.subscription;

import com.harems.api.profile.Profile;
import com.harems.api.profile.ProfileService;
import com.harems.api.subscription.dto.SubscriptionResponse;
import com.harems.api.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final ProfileService profileService;

    public SubscriptionResponse getCurrentSubscription(User user) {
        Profile profile = profileService.getProfile(user);
        profileService.resolveEffectivePlan(profile);

        return new SubscriptionResponse(
                profile.getPlan(),
                SubscriptionStatus.ACTIVE,
                profile.getPlanExpiresAt(),
                profile.getImageCredits(),
                profile.getMessagesUsed()
        );
    }

    @Transactional
    public SubscriptionResponse simulate(User user, PlanType plan) {
        Profile profile = profileService.getProfile(user);
        profileService.applyPlan(profile, plan);

        Subscription subscription = Subscription.builder()
                .user(user)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .startsAt(LocalDateTime.now())
                .endsAt(profile.getPlanExpiresAt())
                .paymentReference("SIMULATED")
                .build();
        subscriptionRepository.save(subscription);

        return new SubscriptionResponse(
                profile.getPlan(),
                SubscriptionStatus.ACTIVE,
                profile.getPlanExpiresAt(),
                profile.getImageCredits(),
                profile.getMessagesUsed()
        );
    }
}
