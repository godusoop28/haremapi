package com.harems.api.payment.paypal.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmSubscriptionRequest(
        @NotBlank(message = "El subscriptionId es obligatorio.")
        String subscriptionId
) {
}
