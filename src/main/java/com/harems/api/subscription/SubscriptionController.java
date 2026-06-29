package com.harems.api.subscription;

import com.harems.api.security.UserPrincipal;
import com.harems.api.subscription.dto.SimulateSubscriptionRequest;
import com.harems.api.subscription.dto.SubscriptionResponse;
import com.harems.api.user.Role;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
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

    /** Solo disponible para ADMIN. No usar en producción para usuarios normales. */
    @PostMapping("/simulate")
    public SubscriptionResponse simulate(@AuthenticationPrincipal UserPrincipal principal,
                                          @Valid @RequestBody SimulateSubscriptionRequest request) {
        if (principal.getUser().getRole() != Role.ADMIN) {
            throw new AccessDeniedException("No tienes permisos para realizar esta acción.");
        }
        return subscriptionService.simulate(principal.getUser(), request.plan());
    }
}
