package com.meterflow.tenancy.api;

import jakarta.validation.constraints.NotBlank;

public record CreateCustomerRequest(
        @NotBlank(message = "Name is required") String name
) {
}