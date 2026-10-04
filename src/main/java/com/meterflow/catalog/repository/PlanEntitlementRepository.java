package com.meterflow.catalog.repository;

import com.meterflow.catalog.domain.PlanEntitlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlanEntitlementRepository
        extends JpaRepository<PlanEntitlement, Long> {

    Optional<PlanEntitlement> findByPlanCodeAndApiProductCode(
            String planCode,
            String productCode);
}