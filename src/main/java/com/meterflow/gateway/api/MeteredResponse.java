package com.meterflow.gateway.api;

import com.meterflow.gateway.service.UsageAuthorization;

public record MeteredResponse(
        Long customerId,
        String productCode,
        Long subscriptionId,
        Long planId,
        long creditCost
) {
    public static MeteredResponse from(UsageAuthorization authorization) {
        return new MeteredResponse(
                authorization.customerId(),
                authorization.productCode(),
                authorization.subscriptionId(),
                authorization.planId(),
                authorization.creditCost()
        );
    }
}
