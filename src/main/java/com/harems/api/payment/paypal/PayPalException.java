package com.harems.api.payment.paypal;

public class PayPalException extends RuntimeException {

    private final int httpStatus;

    public PayPalException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
