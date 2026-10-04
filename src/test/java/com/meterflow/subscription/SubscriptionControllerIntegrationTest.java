package com.meterflow.subscription;

import com.meterflow.catalog.domain.Plan;
import com.meterflow.catalog.repository.PlanRepository;
import com.meterflow.subscription.domain.SubscriptionStatus;
import com.meterflow.subscription.repository.SubscriptionRepository;
import com.meterflow.support.IntegrationTest;
import com.meterflow.tenancy.domain.Customer;
import com.meterflow.tenancy.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
class SubscriptionControllerIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PlanRepository planRepository;

    @BeforeEach
    void setUp() {
        subscriptionRepository.deleteAll();
        planRepository.deleteAll();
        customerRepository.deleteAll();
    }

    // =========================================================
    // SUBSCRIBE
    // =========================================================

    @Test
    void shouldSubscribeCustomerToPlan() throws Exception {

        Customer customer = createCustomer("Amr");
        Plan plan = createPlan("STARTER");

        mockMvc.perform(post(
                        "/customers/{customerId}/subscriptions",
                        customer.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "planCode": "STARTER"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.customerId")
                        .value(customer.getId()))
                .andExpect(jsonPath("$.planId")
                        .value(plan.getId()))
                .andExpect(jsonPath("$.status")
                        .value("ACTIVE"))
                .andExpect(jsonPath("$.startedAt").exists())
                .andExpect(jsonPath("$.currentPeriodStart").exists())
                .andExpect(jsonPath("$.currentPeriodEnd").exists());

        assertEquals(
                1,
                subscriptionRepository.count()
        );
    }

    @Test
    void shouldNormalizePlanCodeWhenSubscribing() throws Exception {

        Customer customer = createCustomer("Amr");
        Plan plan = createPlan("STARTER");

        mockMvc.perform(post(
                        "/customers/{customerId}/subscriptions",
                        customer.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "planCode": "starter"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.planId")
                        .value(plan.getId()));
    }

    @Test
    void shouldReturn400WhenPlanCodeIsBlank() throws Exception {

        Customer customer = createCustomer("Amr");

        mockMvc.perform(post(
                        "/customers/{customerId}/subscriptions",
                        customer.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "planCode": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("VALIDATION_FAILED"));
    }

    @Test
    void shouldReturn404WhenCustomerDoesNotExist()
            throws Exception {

        createPlan("STARTER");

        mockMvc.perform(post(
                        "/customers/{customerId}/subscriptions",
                        999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "planCode": "STARTER"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode")
                        .value("CUSTOMER_NOT_FOUND"));
    }

    @Test
    void shouldReturn400WhenCustomerIsNotActive()
            throws Exception {

        Customer customer = createCustomer("Amr");

        customer.suspend(Instant.now());
        customerRepository.saveAndFlush(customer);

        createPlan("STARTER");

        mockMvc.perform(post(
                        "/customers/{customerId}/subscriptions",
                        customer.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "planCode": "STARTER"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("CUSTOMER_NOT_ACTIVE"));
    }

    @Test
    void shouldReturn404WhenPlanDoesNotExist()
            throws Exception {

        Customer customer = createCustomer("Amr");

        mockMvc.perform(post(
                        "/customers/{customerId}/subscriptions",
                        customer.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "planCode": "UNKNOWN"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode")
                        .value("PLAN_NOT_FOUND"));
    }

    @Test
    void shouldReturn400WhenPlanIsNotActive()
            throws Exception {

        Customer customer = createCustomer("Amr");

        Plan plan = createPlan("STARTER");
        plan.deactivate(Instant.now());
        planRepository.saveAndFlush(plan);

        mockMvc.perform(post(
                        "/customers/{customerId}/subscriptions",
                        customer.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "planCode": "STARTER"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("PLAN_NOT_ACTIVE"));
    }

    @Test
    void shouldReturn409WhenCustomerAlreadyHasActiveSubscription()
            throws Exception {

        Customer customer = createCustomer("Amr");

        createPlan("STARTER");
        createPlan("PRO");

        subscribe(customer.getId(), "STARTER");

        mockMvc.perform(post(
                        "/customers/{customerId}/subscriptions",
                        customer.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "planCode": "PRO"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode")
                        .value("SUBSCRIPTION_ALREADY_ACTIVE"));

        assertEquals(
                1,
                subscriptionRepository.count()
        );
    }

    // =========================================================
    // GET ACTIVE SUBSCRIPTION
    // =========================================================

    @Test
    void shouldGetActiveSubscription() throws Exception {

        Customer customer = createCustomer("Amr");
        Plan plan = createPlan("STARTER");

        subscribe(customer.getId(), "STARTER");

        mockMvc.perform(get(
                        "/customers/{customerId}/subscriptions/active",
                        customer.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId")
                        .value(customer.getId()))
                .andExpect(jsonPath("$.planId")
                        .value(plan.getId()))
                .andExpect(jsonPath("$.status")
                        .value("ACTIVE"));
    }

    @Test
    void shouldReturn404WhenCustomerHasNoActiveSubscription()
            throws Exception {

        Customer customer = createCustomer("Amr");

        mockMvc.perform(get(
                        "/customers/{customerId}/subscriptions/active",
                        customer.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode")
                        .value("SUBSCRIPTION_NOT_FOUND"));
    }

    // =========================================================
    // CANCEL
    // =========================================================

    @Test
    void shouldCancelActiveSubscription() throws Exception {

        Customer customer = createCustomer("Amr");

        createPlan("STARTER");

        subscribe(customer.getId(), "STARTER");

        mockMvc.perform(patch(
                        "/customers/{customerId}/subscriptions/active/cancel",
                        customer.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId")
                        .value(customer.getId()))
                .andExpect(jsonPath("$.status")
                        .value("CANCELLED"));
    }

    @Test
    void shouldReturn404WhenCancellingWithoutActiveSubscription()
            throws Exception {

        Customer customer = createCustomer("Amr");

        mockMvc.perform(patch(
                        "/customers/{customerId}/subscriptions/active/cancel",
                        customer.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode")
                        .value("SUBSCRIPTION_NOT_FOUND"));
    }

    @Test
    void shouldAllowNewSubscriptionAfterCancellation()
            throws Exception {

        Customer customer = createCustomer("Amr");

        createPlan("STARTER");
        createPlan("PRO");

        subscribe(customer.getId(), "STARTER");

        mockMvc.perform(patch(
                        "/customers/{customerId}/subscriptions/active/cancel",
                        customer.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status")
                        .value("CANCELLED"));

        mockMvc.perform(post(
                        "/customers/{customerId}/subscriptions",
                        customer.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "planCode": "PRO"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status")
                        .value("ACTIVE"));

        assertEquals(
                2,
                subscriptionRepository.count()
        );

        assertEquals(
                1,
                subscriptionRepository
                        .findAll()
                        .stream()
                        .filter(subscription ->
                                subscription.getStatus()
                                        == SubscriptionStatus.ACTIVE)
                        .count()
        );
    }

    @Test
    void shouldNotReturnCancelledSubscriptionAsActive()
            throws Exception {

        Customer customer = createCustomer("Amr");

        createPlan("STARTER");

        subscribe(customer.getId(), "STARTER");

        mockMvc.perform(patch(
                        "/customers/{customerId}/subscriptions/active/cancel",
                        customer.getId()))
                .andExpect(status().isOk());

        mockMvc.perform(get(
                        "/customers/{customerId}/subscriptions/active",
                        customer.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode")
                        .value("SUBSCRIPTION_NOT_FOUND"));
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private Customer createCustomer(String name) {

        Customer customer = new Customer(
                name,
                Instant.now()
        );

        return customerRepository.saveAndFlush(customer);
    }

    private Plan createPlan(String code) {

        Plan plan = new Plan(
                code,
                code + " Plan",
                100,
                100000L,
                Instant.now()
        );

        return planRepository.saveAndFlush(plan);
    }

    private void subscribe(
            Long customerId,
            String planCode) throws Exception {

        mockMvc.perform(post(
                        "/customers/{customerId}/subscriptions",
                        customerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "planCode": "%s"
                                }
                                """.formatted(planCode)))
                .andExpect(status().isCreated());
    }
}