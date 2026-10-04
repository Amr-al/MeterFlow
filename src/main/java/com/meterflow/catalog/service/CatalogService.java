package com.meterflow.catalog.service;

import com.meterflow.catalog.api.*;
import com.meterflow.catalog.domain.ApiProduct;
import com.meterflow.catalog.domain.Plan;
import com.meterflow.catalog.domain.PlanEntitlement;
import com.meterflow.catalog.repository.ApiProductRepository;
import com.meterflow.catalog.repository.PlanEntitlementRepository;
import com.meterflow.catalog.repository.PlanRepository;
import com.meterflow.common.error.ErrorCode;
import com.meterflow.common.error.MeterFlowException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class CatalogService {

    private final PlanRepository planRepository;
    private final ApiProductRepository apiProductRepository;
    private final PlanEntitlementRepository planEntitlementRepository;
    private final Clock clock;

    public CatalogService(
            PlanRepository planRepository,
            ApiProductRepository apiProductRepository,
            PlanEntitlementRepository planEntitlementRepository,
            Clock clock) {

        this.planRepository = planRepository;
        this.apiProductRepository = apiProductRepository;
        this.planEntitlementRepository = planEntitlementRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PlanView getPlan(String code) {

        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);

        Plan plan = planRepository.findByCode(normalizedCode)
                .orElseThrow(() -> new MeterFlowException(
                        ErrorCode.PLAN_NOT_FOUND,
                        "Plan not found: " + normalizedCode
                ));

        return new PlanView(
                plan.getId(),
                plan.getCode(),
                plan.getDisplayName(),
                plan.getRequestsPerMinute(),
                plan.getMonthlyQuota(),
                plan.isActive()
        );
    }

    @Transactional
    public PlanResponse createPlan(CreatePlanRequest planRequest) {

        Instant now = Instant.now(clock);

        String code = planRequest.code()
                .trim()
                .toUpperCase(Locale.ROOT);

        if (planRepository.existsByCode(code)) {
            throw new MeterFlowException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "Plan already exists: " + code
            );
        }

        String displayName = planRequest.displayName().trim();

        Plan plan = new Plan(
                code,
                displayName,
                planRequest.requestsPerMinute(),
                planRequest.monthlyQuota(),
                now
        );

        Plan savedPlan = planRepository.save(plan);

        return PlanResponse.from(savedPlan);
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> getPlans() {

        return planRepository.findAll()
                .stream()
                .map(PlanResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PlanResponse getPlanByCode(String requestedCode) {

        if (requestedCode == null || requestedCode.trim().isEmpty()) {
            throw new MeterFlowException(
                    ErrorCode.VALIDATION_FAILED,
                    "Code is required"
            );
        }

        String code = requestedCode
                .trim()
                .toUpperCase(Locale.ROOT);

        Plan plan = planRepository.findByCode(code)
                .orElseThrow(() -> new MeterFlowException(
                        ErrorCode.PLAN_NOT_FOUND,
                        "Plan not found: " + code
                ));

        return PlanResponse.from(plan);
    }

    @Transactional
    public PlanResponse updatePlan(
            UpdatePlanRequest request,
            String requestedCode) {

        String code = requestedCode
                .trim()
                .toUpperCase(Locale.ROOT);

        Plan plan = planRepository.findByCode(code)
                .orElseThrow(() -> new MeterFlowException(
                        ErrorCode.PLAN_NOT_FOUND,
                        "Plan not found: " + code
                ));

        Instant now = Instant.now(clock);

        if (request.active() != null) {
            if (request.active()) {
                plan.activate(now);
            } else {
                plan.deactivate(now);
            }
        }

        if (request.displayName() != null) {

            String displayName = request.displayName().trim();

            if (displayName.isEmpty()) {
                throw new MeterFlowException(
                        ErrorCode.VALIDATION_FAILED,
                        "Display name must not be blank"
                );
            }

            plan.rename(displayName, now);
        }

        if (request.monthlyQuota() != null
                || request.requestsPerMinute() != null) {

            long monthlyQuota =
                    request.monthlyQuota() != null
                            ? request.monthlyQuota()
                            : plan.getMonthlyQuota();

            int requestsPerMinute =
                    request.requestsPerMinute() != null
                            ? request.requestsPerMinute()
                            : plan.getRequestsPerMinute();

            plan.updateLimits(
                    requestsPerMinute,
                    monthlyQuota,
                    now
            );
        }

        return PlanResponse.from(plan);
    }

    @Transactional
    public ApiProductResponse createApiProduct(
            CreateApiProductRequest request) {

        String code = request.code()
                .trim()
                .toUpperCase(Locale.ROOT);

        String displayName = request.displayName().trim();

        if (apiProductRepository.existsByCode(code)) {
            throw new MeterFlowException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "API product already exists: " + code
            );
        }

        Instant now = Instant.now(clock);

        ApiProduct apiProduct = new ApiProduct(
                code,
                displayName,
                now
        );

        ApiProduct savedProduct =
                apiProductRepository.save(apiProduct);

        return ApiProductResponse.from(savedProduct);
    }

    @Transactional(readOnly = true)
    public Page<ApiProductResponse> getAllApiProducts(int page) {

        Pageable pageable = PageRequest.of(page, 10);

        return apiProductRepository.findAll(pageable)
                .map(ApiProductResponse::from);
    }

    @Transactional
    public ApiProductResponse updateApiProductStatus(
            UpdateApiProductRequest request,
            String requestedCode) {

        String code = requestedCode
                .trim()
                .toUpperCase(Locale.ROOT);

        Instant now = Instant.now(clock);

        ApiProduct apiProduct =
                apiProductRepository.findByCode(code)
                        .orElseThrow(() -> new MeterFlowException(
                                ErrorCode.PRODUCT_NOT_FOUND,
                                "Product not found: " + code
                        ));

        if (request.active()) {
            apiProduct.activate(now);
        } else {
            apiProduct.deactivate(now);
        }

        return ApiProductResponse.from(apiProduct);
    }

    @Transactional
    public PlanEntitlementResponse updatePlanEntitlement(
            UpsertPlanEntitlementRequest request,
            String requestedPlanCode,
            String requestedProductCode) {

        if (requestedPlanCode == null
                || requestedPlanCode.trim().isEmpty()
                || requestedProductCode == null
                || requestedProductCode.trim().isEmpty()) {

            throw new MeterFlowException(
                    ErrorCode.INVALID_REQUEST,
                    "Plan code and product code are required"
            );
        }

        String planCode = requestedPlanCode
                .trim()
                .toUpperCase(Locale.ROOT);

        String productCode = requestedProductCode
                .trim()
                .toUpperCase(Locale.ROOT);

        Optional<PlanEntitlement> existingEntitlement =
                planEntitlementRepository
                        .findByPlanCodeAndApiProductCode(
                                planCode,
                                productCode
                        );

        if (existingEntitlement.isPresent()) {

            PlanEntitlement entitlement =
                    existingEntitlement.get();

            entitlement.updateEntitlement(
                    request.allowed(),
                    request.creditCost()
            );

            return PlanEntitlementResponse.from(entitlement);
        }

        Plan plan = planRepository.findByCode(planCode)
                .orElseThrow(() -> new MeterFlowException(
                        ErrorCode.PLAN_NOT_FOUND,
                        "Plan not found: " + planCode
                ));

        ApiProduct apiProduct =
                apiProductRepository.findByCode(productCode)
                        .orElseThrow(() -> new MeterFlowException(
                                ErrorCode.PRODUCT_NOT_FOUND,
                                "Product not found: " + productCode
                        ));

        PlanEntitlement entitlement =
                new PlanEntitlement(
                        plan,
                        apiProduct,
                        request.allowed(),
                        request.creditCost()
                );

        PlanEntitlement savedEntitlement =
                planEntitlementRepository.save(entitlement);

        return PlanEntitlementResponse.from(savedEntitlement);
    }
}