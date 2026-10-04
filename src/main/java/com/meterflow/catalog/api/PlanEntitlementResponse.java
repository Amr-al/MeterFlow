package com.meterflow.catalog.api;

import com.meterflow.catalog.domain.PlanEntitlement;

public record PlanEntitlementResponse(
        Long id,
        String planCode,
        String productCode,
        boolean allowed,
        long creditCost
) {
    public static PlanEntitlementResponse from(
            PlanEntitlement entitlement) {

        return new PlanEntitlementResponse(
                entitlement.getId(),
                entitlement.getPlan().getCode(),
                entitlement.getApiProduct().getCode(),
                entitlement.isAllowed(),
                entitlement.getCreditCost()
        );
    }
}