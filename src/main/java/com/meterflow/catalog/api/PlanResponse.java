package com.meterflow.catalog.api;

import com.meterflow.catalog.domain.Plan;

import java.time.Instant;

public record PlanResponse(
        Long id,
        String code,
        String displayName,
        int requestsPerMinute,
        long monthlyQuota,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static PlanResponse from(Plan plan){
        return new PlanResponse(
                plan.getId(),
                plan.getCode(),
                plan.getDisplayName(),
                plan.getRequestsPerMinute(),
                plan.getMonthlyQuota(),
                plan.isActive(),
                plan.getCreatedAt(),
                plan.getUpdatedAt()
        );
    }
}
