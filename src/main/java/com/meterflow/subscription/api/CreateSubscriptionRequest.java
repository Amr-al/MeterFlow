package com.meterflow.subscription.api;

import jakarta.validation.constraints.NotBlank;

public record CreateSubscriptionRequest(
        @NotBlank String planCode
) {}
