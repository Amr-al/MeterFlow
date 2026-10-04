package com.meterflow.catalog.api;

import jakarta.validation.constraints.Positive;

public record UpdatePlanRequest(

        String displayName,

        @Positive(message = "Requests per minute must be greater than 0")
        Integer requestsPerMinute,

        @Positive(message = "Monthly quota must be greater than 0")
        Long monthlyQuota,

        Boolean active
) {
}

