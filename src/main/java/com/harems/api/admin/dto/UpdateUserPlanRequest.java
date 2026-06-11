package com.harems.api.admin.dto;

import com.harems.api.subscription.PlanType;
import jakarta.validation.constraints.NotNull;

public record UpdateUserPlanRequest(
        @NotNull(message = "El plan es obligatorio.")
        PlanType plan
) {
}
