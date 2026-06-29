package com.harems.api.payment;

import com.harems.api.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByPaypalSubscriptionId(String paypalSubscriptionId);
    Optional<Payment> findTopByUserAndStatusOrderByCreatedAtDesc(User user, PaymentStatus status);
}
