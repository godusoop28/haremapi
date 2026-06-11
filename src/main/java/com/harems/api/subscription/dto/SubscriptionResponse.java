package com.harems.api.subscription.dto;

import com.harems.api.subscription.PlanType;
import com.harems.api.subscription.SubscriptionStatus;

import java.time.LocalDateTime;

public record SubscriptionResponse(
        PlanType plan,
        SubscriptionStatus status,
        LocalDateTime expiresAt,
        Integer imageCredits,
        Integer messagesUsed
) {
}
