package com.meterflow.gateway.api;

import jakarta.validation.constraints.NotBlank;

public record MeteredRequest(
        @NotBlank String productCode
) {
}