package com.meterflow.gateway.service;

import com.meterflow.catalog.CatalogFacade;
import com.meterflow.catalog.api.ApiProductView;
import com.meterflow.catalog.api.EntitlementView;
import com.meterflow.common.error.ErrorCode;
import com.meterflow.common.error.MeterFlowException;
import com.meterflow.subscription.SubscriptionFacade;
import com.meterflow.subscription.api.SubscriptionView;
import com.meterflow.tenancy.CustomerFacade;
import com.meterflow.tenancy.api.CustomerView;
import com.meterflow.tenancy.domain.CustomerStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeteringService {

    private final CustomerFacade customerFacade;
    private final CatalogFacade catalogFacade;
    private final SubscriptionFacade subscriptionFacade;

    public MeteringService(
            CustomerFacade customerFacade,
            CatalogFacade catalogFacade,
            SubscriptionFacade subscriptionFacade
    ) {
        this.customerFacade = customerFacade;
        this.catalogFacade = catalogFacade;
        this.subscriptionFacade = subscriptionFacade;
    }

    @Transactional(readOnly = true)
    public UsageAuthorization authorize(Long customerId, String productCode) {

        CustomerView customer = customerFacade.getCustomer(customerId);

        if (customer.status() != CustomerStatus.ACTIVE) {
            throw new MeterFlowException(
                    ErrorCode.CUSTOMER_SUSPENDED,
                    "Customer is suspended: " + customerId
            );
        }

        ApiProductView product = catalogFacade.getApiProduct(productCode);

        if (!product.active()) {
            throw new MeterFlowException(
                    ErrorCode.PRODUCT_INACTIVE,
                    "Product is not active: " + product.code()
            );
        }

        SubscriptionView subscription = subscriptionFacade
                .findActiveSubscription(customerId)
                .orElseThrow(() -> new MeterFlowException(
                        ErrorCode.NO_ACTIVE_SUBSCRIPTION,
                        "No active subscription for customer: " + customerId
                ));

        EntitlementView entitlement = catalogFacade
                .findEntitlement(subscription.planId(), product.id())
                .filter(EntitlementView::allowed)
                .orElseThrow(() -> new MeterFlowException(
                        ErrorCode.PRODUCT_NOT_ENTITLED,
                        "Product " + product.code() + " is not included in the current plan"
                ));

        return new UsageAuthorization(
                customerId,
                subscription.id(),
                subscription.planId(),
                product.id(),
                product.code(),
                entitlement.creditCost()
        );
    }
}
