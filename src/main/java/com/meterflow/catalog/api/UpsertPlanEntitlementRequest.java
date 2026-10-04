package com.meterflow.catalog.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpsertPlanEntitlementRequest(

        @NotNull
        Boolean allowed,

        @NotNull
        @PositiveOrZero
        Long creditCost

) {
}