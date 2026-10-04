package com.meterflow.subscription.service;

import com.meterflow.catalog.CatalogFacade;
import com.meterflow.catalog.api.PlanView;
import com.meterflow.common.error.ErrorCode;
import com.meterflow.common.error.MeterFlowException;
import com.meterflow.subscription.api.SubscriptionResponse;
import com.meterflow.subscription.domain.Subscription;
import com.meterflow.subscription.domain.SubscriptionStatus;
import com.meterflow.subscription.repository.SubscriptionRepository;
import com.meterflow.tenancy.CustomerFacade;
import com.meterflow.tenancy.api.CustomerView;
import com.meterflow.tenancy.domain.CustomerStatus;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import org.hibernate.exception.ConstraintViolationException;
import java.time.ZoneOffset;

@Service
public class SubscriptionService {
    private final SubscriptionRepository subscriptionRepository;
    private final CustomerFacade customerFacade;
    private final CatalogFacade catalogFacade;
    private final Clock clock;

    public SubscriptionService(
            SubscriptionRepository subscriptionRepository,
            CustomerFacade customerFacade,
            CatalogFacade catalogFacade,
            Clock clock) {

        this.subscriptionRepository = subscriptionRepository;
        this.customerFacade = customerFacade;
        this.catalogFacade = catalogFacade;
        this.clock = clock;
    }


    @Transactional
    public SubscriptionResponse subscribe(Long customerId, String planCode) {

        CustomerView customer = customerFacade.getCustomer(customerId);

        if (customer.status() != CustomerStatus.ACTIVE) {
            throw new MeterFlowException(
                    ErrorCode.CUSTOMER_NOT_ACTIVE,
                    "Customer is not active: " + customerId
            );
        }

        PlanView plan = catalogFacade.getPlan(planCode);

        if (!plan.active()) {
            throw new MeterFlowException(
                    ErrorCode.PLAN_NOT_ACTIVE,
                    "Plan is not active: " + planCode
            );
        }

        if (subscriptionRepository
                .findByCustomerIdAndStatus(
                        customerId,
                        SubscriptionStatus.ACTIVE)
                .isPresent()) {

            throw new MeterFlowException(
                    ErrorCode.SUBSCRIPTION_ALREADY_ACTIVE,
                    "Customer already has an active subscription: " + customerId
            );
        }

        Instant now = Instant.now(clock);

        Instant periodEnd = now
                .atZone(ZoneOffset.UTC)
                .plusMonths(1)
                .toInstant();

        Subscription subscription = new Subscription(
                customer.id(),
                plan.id(),
                now,
                now,
                periodEnd
        );

        try {

            Subscription savedSubscription =
                    subscriptionRepository.saveAndFlush(subscription);

            return SubscriptionResponse.from(savedSubscription);

        } catch (DataIntegrityViolationException ex) {

            if (isActiveSubscriptionUniqueViolation(ex)) {
                throw new MeterFlowException(
                        ErrorCode.SUBSCRIPTION_ALREADY_ACTIVE,
                        "Customer already has an active subscription: " + customerId
                );
            }

            throw ex;
        }
    }

    @Transactional
    public SubscriptionResponse getActiveSubscription(Long customerId){
        Subscription subscription = subscriptionRepository
                .findByCustomerIdAndStatus(customerId, SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new MeterFlowException(
                        ErrorCode.SUBSCRIPTION_NOT_FOUND,
                        "No active subscription found for customer: " + customerId
                ));
        return SubscriptionResponse.from(subscription);
    }

    @Transactional
    public SubscriptionResponse cancelActiveSubscription(Long customerId){
        Subscription subscription = subscriptionRepository
                .findByCustomerIdAndStatus(customerId, SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new MeterFlowException(
                        ErrorCode.SUBSCRIPTION_NOT_FOUND,
                        "No active subscription found for customer: " + customerId
                ));

        Instant now = Instant.now(clock);

        subscription.cancel(now);

        return SubscriptionResponse.from(subscription);
    }


    private boolean isActiveSubscriptionUniqueViolation(Throwable throwable) {

        Throwable current = throwable;

        while (current != null) {

            if (current instanceof ConstraintViolationException ex) {

                return "uq_subscription_active_per_customer"
                        .equals(ex.getConstraintName());
            }

            current = current.getCause();
        }

        return false;
    }
}
