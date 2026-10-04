package com.meterflow.catalog;

import com.meterflow.catalog.domain.ApiProduct;
import com.meterflow.catalog.domain.Plan;
import com.meterflow.catalog.repository.ApiProductRepository;
import com.meterflow.catalog.repository.PlanEntitlementRepository;
import com.meterflow.catalog.repository.PlanRepository;
import com.meterflow.subscription.repository.SubscriptionRepository;
import com.meterflow.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
class PlanAdminControllerIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private ApiProductRepository apiProductRepository;

    @Autowired
    private PlanEntitlementRepository planEntitlementRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @BeforeEach
    void setUp() {
        subscriptionRepository.deleteAllInBatch();
        planEntitlementRepository.deleteAllInBatch();
        planRepository.deleteAllInBatch();
        apiProductRepository.deleteAllInBatch();
    }

    // =========================================================
    // PLAN CREATE
    // =========================================================

    @Test
    void shouldCreatePlan() throws Exception {

        mockMvc.perform(post("/admin/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "STARTER",
                                  "displayName": "Starter Plan",
                                  "requestsPerMinute": 100,
                                  "monthlyQuota": 100000
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.code").value("STARTER"))
                .andExpect(jsonPath("$.displayName").value("Starter Plan"))
                .andExpect(jsonPath("$.requestsPerMinute").value(100))
                .andExpect(jsonPath("$.monthlyQuota").value(100000))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void shouldNormalizePlanCodeWhenCreating() throws Exception {

        mockMvc.perform(post("/admin/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": " starter ",
                                  "displayName": "Starter Plan",
                                  "requestsPerMinute": 100,
                                  "monthlyQuota": 100000
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("STARTER"));
    }

    @Test
    void shouldReturn409WhenPlanCodeAlreadyExists() throws Exception {

        createPlan("STARTER", "Starter Plan");

        mockMvc.perform(post("/admin/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "starter",
                                  "displayName": "Another Starter",
                                  "requestsPerMinute": 200,
                                  "monthlyQuota": 200000
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode")
                        .value("DUPLICATE_RESOURCE"));
    }

    @Test
    void shouldReturn400WhenRequestsPerMinuteIsZero() throws Exception {

        mockMvc.perform(post("/admin/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "STARTER",
                                  "displayName": "Starter Plan",
                                  "requestsPerMinute": 0,
                                  "monthlyQuota": 100000
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("VALIDATION_FAILED"));
    }

    @Test
    void shouldReturn400WhenMonthlyQuotaIsNegative() throws Exception {

        mockMvc.perform(post("/admin/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "STARTER",
                                  "displayName": "Starter Plan",
                                  "requestsPerMinute": 100,
                                  "monthlyQuota": -1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("VALIDATION_FAILED"));
    }

    // =========================================================
    // PLAN GET
    // =========================================================

    @Test
    void shouldGetPlanByCode() throws Exception {

        createPlan("STARTER", "Starter Plan");

        mockMvc.perform(get("/admin/plans/STARTER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("STARTER"))
                .andExpect(jsonPath("$.displayName").value("Starter Plan"))
                .andExpect(jsonPath("$.requestsPerMinute").value(100))
                .andExpect(jsonPath("$.monthlyQuota").value(100000))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void shouldNormalizePlanCodeWhenGettingPlan() throws Exception {

        createPlan("STARTER", "Starter Plan");

        mockMvc.perform(get("/admin/plans/starter"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("STARTER"));
    }

    @Test
    void shouldReturn404WhenPlanDoesNotExist() throws Exception {

        mockMvc.perform(get("/admin/plans/UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode")
                        .value("PLAN_NOT_FOUND"));
    }

    @Test
    void shouldReturnAllPlans() throws Exception {

        createPlan("STARTER", "Starter Plan");
        createPlan("PRO", "Pro Plan");

        mockMvc.perform(get("/admin/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    // =========================================================
    // PLAN PATCH
    // =========================================================

    @Test
    void shouldDeactivatePlan() throws Exception {

        createPlan("STARTER", "Starter Plan");

        mockMvc.perform(patch("/admin/plans/STARTER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "active": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("STARTER"))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void shouldRenamePlan() throws Exception {

        createPlan("STARTER", "Starter Plan");

        mockMvc.perform(patch("/admin/plans/STARTER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Starter Plus"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName")
                        .value("Starter Plus"));
    }

    @Test
    void shouldTrimDisplayNameWhenUpdatingPlan() throws Exception {

        createPlan("STARTER", "Starter Plan");

        mockMvc.perform(patch("/admin/plans/STARTER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "  Starter Plus  "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName")
                        .value("Starter Plus"));
    }

    @Test
    void shouldUpdateOnlyRequestsPerMinute() throws Exception {

        createPlan("STARTER", "Starter Plan");

        mockMvc.perform(patch("/admin/plans/STARTER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestsPerMinute": 250
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestsPerMinute").value(250))
                .andExpect(jsonPath("$.monthlyQuota").value(100000));
    }

    @Test
    void shouldUpdateOnlyMonthlyQuota() throws Exception {

        createPlan("STARTER", "Starter Plan");

        mockMvc.perform(patch("/admin/plans/STARTER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "monthlyQuota": 500000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestsPerMinute").value(100))
                .andExpect(jsonPath("$.monthlyQuota").value(500000));
    }

    @Test
    void shouldUpdateMultiplePlanFieldsAtOnce() throws Exception {

        createPlan("STARTER", "Starter Plan");

        mockMvc.perform(patch("/admin/plans/STARTER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Starter Plus",
                                  "requestsPerMinute": 250,
                                  "monthlyQuota": 500000,
                                  "active": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName")
                        .value("Starter Plus"))
                .andExpect(jsonPath("$.requestsPerMinute")
                        .value(250))
                .andExpect(jsonPath("$.monthlyQuota")
                        .value(500000))
                .andExpect(jsonPath("$.active")
                        .value(false));
    }

    @Test
    void shouldReturn400WhenPatchDisplayNameIsBlank() throws Exception {

        createPlan("STARTER", "Starter Plan");

        mockMvc.perform(patch("/admin/plans/STARTER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("VALIDATION_FAILED"));
    }

    @Test
    void shouldReturn404WhenUpdatingUnknownPlan() throws Exception {

        mockMvc.perform(patch("/admin/plans/UNKNOWN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "active": false
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode")
                        .value("PLAN_NOT_FOUND"));
    }

    // =========================================================
    // PLAN ENTITLEMENT
    // =========================================================

    @Test
    void shouldCreatePlanEntitlement() throws Exception {

        createPlan("STARTER", "Starter Plan");
        createProduct("SMS", "SMS API");

        mockMvc.perform(put(
                        "/admin/plans/STARTER/entitlements/SMS")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "allowed": true,
                                  "creditCost": 1000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.planCode")
                        .value("STARTER"))
                .andExpect(jsonPath("$.productCode")
                        .value("SMS"))
                .andExpect(jsonPath("$.allowed")
                        .value(true))
                .andExpect(jsonPath("$.creditCost")
                        .value(1000));
    }

    @Test
    void shouldUpdateExistingPlanEntitlement() throws Exception {

        createPlan("STARTER", "Starter Plan");
        createProduct("SMS", "SMS API");

        mockMvc.perform(put(
                        "/admin/plans/STARTER/entitlements/SMS")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "allowed": true,
                                  "creditCost": 1000
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put(
                        "/admin/plans/STARTER/entitlements/SMS")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "allowed": false,
                                  "creditCost": 2000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed")
                        .value(false))
                .andExpect(jsonPath("$.creditCost")
                        .value(2000));
    }

    @Test
    void shouldNotCreateDuplicateEntitlementWhenUpdating()
            throws Exception {

        createPlan("STARTER", "Starter Plan");
        createProduct("SMS", "SMS API");

        mockMvc.perform(put(
                        "/admin/plans/STARTER/entitlements/SMS")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "allowed": true,
                                  "creditCost": 1000
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put(
                        "/admin/plans/STARTER/entitlements/SMS")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "allowed": true,
                                  "creditCost": 2000
                                }
                                """))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals(
                1,
                planEntitlementRepository.count()
        );
    }

    @Test
    void shouldNormalizeCodesWhenUpsertingEntitlement()
            throws Exception {

        createPlan("STARTER", "Starter Plan");
        createProduct("SMS", "SMS API");

        mockMvc.perform(put(
                        "/admin/plans/starter/entitlements/sms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "allowed": true,
                                  "creditCost": 1000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planCode")
                        .value("STARTER"))
                .andExpect(jsonPath("$.productCode")
                        .value("SMS"));
    }

    @Test
    void shouldReturn404WhenEntitlementPlanDoesNotExist()
            throws Exception {

        createProduct("SMS", "SMS API");

        mockMvc.perform(put(
                        "/admin/plans/UNKNOWN/entitlements/SMS")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "allowed": true,
                                  "creditCost": 1000
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode")
                        .value("PLAN_NOT_FOUND"));
    }

    @Test
    void shouldReturn404WhenEntitlementProductDoesNotExist()
            throws Exception {

        createPlan("STARTER", "Starter Plan");

        mockMvc.perform(put(
                        "/admin/plans/STARTER/entitlements/UNKNOWN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "allowed": true,
                                  "creditCost": 1000
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode")
                        .value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void shouldReturn400WhenCreditCostIsNegative()
            throws Exception {

        createPlan("STARTER", "Starter Plan");
        createProduct("SMS", "SMS API");

        mockMvc.perform(put(
                        "/admin/plans/STARTER/entitlements/SMS")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "allowed": true,
                                  "creditCost": -1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("VALIDATION_FAILED"));
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private Plan createPlan(
            String code,
            String displayName) {

        Plan plan = new Plan(
                code,
                displayName,
                100,
                100000L,
                Instant.now()
        );

        return planRepository.saveAndFlush(plan);
    }

    private ApiProduct createProduct(
            String code,
            String displayName) {

        ApiProduct product = new ApiProduct(
                code,
                displayName,
                Instant.now()
        );

        return apiProductRepository.saveAndFlush(product);
    }
}