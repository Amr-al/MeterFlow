package com.meterflow.catalog.api;

import com.meterflow.catalog.domain.ApiProduct;

public record ApiProductView(
        Long id,
        String code,
        boolean active
) {
    public static ApiProductView from(ApiProduct product) {
        return new ApiProductView(
                product.getId(),
                product.getCode(),
                product.isActive()
        );
    }
}
