package com.harems.api.payment.dto;

import com.harems.api.subscription.PlanType;
import jakarta.validation.constraints.NotNull;

public record CheckoutRequest(
        @NotNull(message = "El plan es obligatorio.")
        PlanType plan
) {
}
