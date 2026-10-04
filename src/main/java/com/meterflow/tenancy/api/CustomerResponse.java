package com.meterflow.tenancy.api;

import com.meterflow.tenancy.domain.CustomerStatus;

public record CustomerResponse(
        Long id,
        String name,
        CustomerStatus status
) {
}
