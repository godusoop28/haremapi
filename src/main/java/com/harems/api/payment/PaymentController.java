package com.harems.api.payment;

import com.harems.api.payment.dto.PaymentResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints legacy — reemplazados por /payments/paypal/*.
 * Devuelven 410 Gone para evitar confusión.
 */
@RestController
@RequestMapping("/payments")
public class PaymentController {

    @Deprecated
    @PostMapping("/checkout")
    public ResponseEntity<PaymentResponse> checkout() {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(new PaymentResponse(
                        "Este endpoint ya no está activo. " +
                        "Usa POST /api/payments/paypal/create-subscription para iniciar una suscripción PayPal."));
    }

    @Deprecated
    @PostMapping("/webhook")
    public ResponseEntity<PaymentResponse> webhook() {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(new PaymentResponse(
                        "Este endpoint ya no está activo. " +
                        "El webhook de PayPal es POST /api/payments/paypal/webhook"));
    }
}
