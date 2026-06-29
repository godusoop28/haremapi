package com.harems.api.payment;

public enum PaymentStatus {
    CREATED,
    PENDING,
    ACTIVE,
    CANCEL_PENDING,
    CANCELLED,
    SUSPENDED,
    EXPIRED,
    FAILED,
    REFUNDED
}
