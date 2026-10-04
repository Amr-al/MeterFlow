package com.meterflow.catalog.api;

import com.meterflow.catalog.domain.ApiProduct;

import java.time.Instant;

public record ApiProductResponse(
        Long id,
        String code,
        String displayName,
        boolean active,
        Instant createdAt
) {
    public static ApiProductResponse from(ApiProduct product) {
        return new ApiProductResponse(
                product.getId(),
                product.getCode(),
                product.getDisplayName(),
                product.isActive(),
                product.getCreatedAt()
        );
    }
}
