package com.harems.api.payment;

import com.harems.api.payment.dto.CheckoutRequest;
import com.harems.api.payment.dto.PaymentResponse;
import com.harems.api.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Placeholder endpoints for the future payment integration
 * (Mercado Pago, Conekta, OpenPay, etc). No real payment provider is
 * connected yet.
 */
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    @PostMapping("/checkout")
    public PaymentResponse checkout(@AuthenticationPrincipal UserPrincipal principal,
                                     @Valid @RequestBody CheckoutRequest request) {
        return new PaymentResponse("Payment integration pending");
    }

    @PostMapping("/webhook")
    public PaymentResponse webhook() {
        return new PaymentResponse("Payment integration pending");
    }
}
