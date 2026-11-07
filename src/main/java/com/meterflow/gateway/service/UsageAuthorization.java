package com.meterflow.gateway.service;

public record UsageAuthorization(
        Long customerId,
        Long subscriptionId,
        Long planId,
        Long apiProductId,
        String productCode,
        long creditCost
) {
}
