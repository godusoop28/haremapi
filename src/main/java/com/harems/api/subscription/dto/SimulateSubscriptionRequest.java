package com.harems.api.subscription.dto;

import com.harems.api.subscription.PlanType;
import jakarta.validation.constraints.NotNull;

public record SimulateSubscriptionRequest(
        @NotNull(message = "El plan es obligatorio.")
        PlanType plan
) {
}
