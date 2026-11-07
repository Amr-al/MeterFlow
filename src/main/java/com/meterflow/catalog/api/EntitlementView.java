package com.meterflow.catalog.api;

import com.meterflow.catalog.domain.PlanEntitlement;

public record EntitlementView(
        Long id,
        boolean allowed,
        long creditCost
) {
    public static EntitlementView from(PlanEntitlement entitlement) {
        return new EntitlementView(
                entitlement.getId(),
                entitlement.isAllowed(),
                entitlement.getCreditCost()
        );
    }
}
