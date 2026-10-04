package com.meterflow.catalog.api;

import jakarta.validation.constraints.NotBlank;

public record CreateApiProductRequest(
        @NotBlank(message = "code is required") String code,
        @NotBlank(message = "displayName is required") String displayName
) {
}
