package com.meterflow.subscription.api;

import java.time.Instant;

import com.meterflow.subscription.domain.Subscription;

public record SubscriptionView(
        Long id,
        Long customerId,
        Long planId,
        Instant currentPeriodStart,
        Instant currentPeriodEnd
) {
        public static SubscriptionView from(Subscription subscription){
                return new SubscriptionView(
                        subscription.getId(),
                        subscription.getCustomerId(),
                        subscription.getPlanId(),
                        subscription.getCurrentPeriodStart(),
                        subscription.getCurrentPeriodEnd()
                );
        }
}
