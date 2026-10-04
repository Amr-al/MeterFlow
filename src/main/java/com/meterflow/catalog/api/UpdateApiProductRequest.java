package com.meterflow.catalog.api;

import jakarta.validation.constraints.NotNull;

public record UpdateApiProductRequest(
        @NotNull(message = "Active is required") Boolean active
) {
}
