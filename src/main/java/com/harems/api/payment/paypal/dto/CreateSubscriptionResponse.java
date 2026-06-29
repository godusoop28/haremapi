package com.harems.api.payment.paypal.dto;

import com.harems.api.subscription.PlanType;
import lombok.Builder;

@Builder
public record CreateSubscriptionResponse(
        String provider,
        PlanType plan,
        String paypalPlanId,
        String paypalSubscriptionId,
        String approvalUrl,
        String status
) {
}
