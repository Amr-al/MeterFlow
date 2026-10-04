package com.meterflow.catalog.api;

public record PlanView(
        Long id,
        String code,
        String displayName,
        int requestsPerMinute,
        long monthlyQuota,
        boolean active
) {
}
