package com.meterflow.subscription.repository;

import com.meterflow.subscription.domain.Subscription;
import com.meterflow.subscription.domain.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubscriptionRepository
        extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByCustomerIdAndStatus(
            Long customerId,
            SubscriptionStatus status);
}