package com.harems.api.subscription;

import com.harems.api.security.UserPrincipal;
import com.harems.api.subscription.dto.SimulateSubscriptionRequest;
import com.harems.api.subscription.dto.SubscriptionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping("/me")
    public SubscriptionResponse getMySubscription(@AuthenticationPrincipal UserPrincipal principal) {
        return subscriptionService.getCurrentSubscription(principal.getUser());
    }

    /**
     * Temporary endpoint to simulate plan changes during development.
     * Will be replaced once a real payment provider is integrated.
     */
    @PostMapping("/simulate")
    public SubscriptionResponse simulate(@AuthenticationPrincipal UserPrincipal principal,
                                          @Valid @RequestBody SimulateSubscriptionRequest request) {
        return subscriptionService.simulate(principal.getUser(), request.plan());
    }
}
