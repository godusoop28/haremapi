package com.harems.api.subscription;

import com.harems.api.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findByUserOrderByCreatedAtDesc(User user);

    Optional<Subscription> findByPaypalSubscriptionId(String paypalSubscriptionId);
}
