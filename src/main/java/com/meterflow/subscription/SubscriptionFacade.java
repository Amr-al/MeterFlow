package com.meterflow.subscription;

import com.meterflow.subscription.api.SubscriptionView;
import com.meterflow.subscription.service.SubscriptionService;
import org.springframework.stereotype.Component;

import java.util.Optional;


@Component
public class SubscriptionFacade {

    private final SubscriptionService subscriptionService;

    public SubscriptionFacade(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    public Optional<SubscriptionView> findActiveSubscription(Long customerId) {
        return subscriptionService.findActiveSubscription(customerId);
    }
}
