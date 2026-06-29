package com.harems.api.payment.paypal.dto;

import com.harems.api.subscription.PlanType;
import jakarta.validation.constraints.NotNull;

public record CreateSubscriptionRequest(
        @NotNull(message = "El plan es obligatorio.")
        PlanType plan
) {
}
