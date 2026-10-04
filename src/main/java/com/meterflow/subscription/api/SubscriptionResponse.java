package com.meterflow.subscription.api;

import com.meterflow.subscription.domain.Subscription;
import com.meterflow.subscription.domain.SubscriptionStatus;

import java.time.Instant;

public record SubscriptionResponse(
        Long id,
        Long customerId,
        Long planId,
        SubscriptionStatus status,
        Instant startedAt,
        Instant currentPeriodStart,
        Instant currentPeriodEnd
) {
    public static SubscriptionResponse from(Subscription subscription){
        return new SubscriptionResponse(subscription.getId(),subscription.getCustomerId(),
                subscription.getPlanId(),subscription.getStatus(),subscription.getStartedAt(),subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd());
    }
}
