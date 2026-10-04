package com.meterflow.catalog.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreatePlanRequest(
        @NotBlank(message = "Code is required") String code,
        @NotBlank(message = "Display name is required") String displayName,
        @NotNull(message = "Requests per minute is required") @Positive(message = "Requests per minute must be greater than 0") Integer requestsPerMinute,
        @NotNull(message = "Monthly quota is required") @Positive(message = "Monthly quota must be greater than 0") Long monthlyQuota
) {
}