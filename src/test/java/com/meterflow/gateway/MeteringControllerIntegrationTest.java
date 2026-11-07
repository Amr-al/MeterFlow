package com.meterflow.gateway;

import com.meterflow.catalog.domain.ApiProduct;
import com.meterflow.catalog.domain.Plan;
import com.meterflow.catalog.domain.PlanEntitlement;
import com.meterflow.catalog.repository.ApiProductRepository;
import com.meterflow.catalog.repository.PlanEntitlementRepository;
import com.meterflow.catalog.repository.PlanRepository;
import com.meterflow.credential.repository.ApiKeyRepository;
import com.meterflow.credential.service.ApiKeyService;
import com.meterflow.subscription.domain.Subscription;
import com.meterflow.subscription.repository.SubscriptionRepository;
import com.meterflow.support.IntegrationTest;
import com.meterflow.tenancy.domain.Customer;
import com.meterflow.tenancy.repository.CustomerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MeteringControllerIntegrationTest extends IntegrationTest {

    private static final long SMS_CREDIT_COST = 1500L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private ApiProductRepository apiProductRepository;

    @Autowired
    private PlanEntitlementRepository planEntitlementRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private ApiKeyService apiKeyService;

    private Customer customer;
    private Plan plan;
    private ApiProduct sms;
    private String apiKey;

    @BeforeEach
    void setUp() {
        cleanDatabase();

        Instant now = Instant.now();

        customer = customerRepository.saveAndFlush(new Customer("Customer A", now));
        plan = planRepository.saveAndFlush(new Plan("PRO", "Pro Plan", 60, 10_000L, now));
        sms = apiProductRepository.saveAndFlush(new ApiProduct("SMS", "SMS API", now));
        apiKey = apiKeyService.createApiKey(customer.getId()).apiKey();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private void cleanDatabase() {
        apiKeyRepository.deleteAllInBatch();
        subscriptionRepository.deleteAllInBatch();
        planEntitlementRepository.deleteAllInBatch();
        apiProductRepository.deleteAllInBatch();
        planRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();
    }

    @Test
    void shouldAuthorizeUsageAndReturnCreditCost() throws Exception {

        Subscription subscription = subscribe();
        entitle(true);

        recordUsage("SMS")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(customer.getId()))
                .andExpect(jsonPath("$.productCode").value("SMS"))
                .andExpect(jsonPath("$.subscriptionId").value(subscription.getId()))
                .andExpect(jsonPath("$.planId").value(plan.getId()))
                .andExpect(jsonPath("$.creditCost").value(SMS_CREDIT_COST));
    }

    @Test
    void shouldNormalizeProductCode() throws Exception {

        subscribe();
        entitle(true);

        recordUsage("  sms ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode").value("SMS"));
    }

    @Test
    void shouldRejectSuspendedCustomer() throws Exception {

        subscribe();
        entitle(true);

        customer.suspend(Instant.now());
        customerRepository.saveAndFlush(customer);

        recordUsage("SMS")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CUSTOMER_SUSPENDED"));
    }

    @Test
    void shouldReturnNotFoundForUnknownProduct() throws Exception {

        subscribe();
        entitle(true);

        recordUsage("UNKNOWN")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void shouldRejectInactiveProduct() throws Exception {

        subscribe();
        entitle(true);

        sms.deactivate(Instant.now());
        apiProductRepository.saveAndFlush(sms);

        recordUsage("SMS")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_INACTIVE"));
    }

    @Test
    void shouldRejectCustomerWithoutSubscription() throws Exception {

        entitle(true);

        recordUsage("SMS")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NO_ACTIVE_SUBSCRIPTION"));
    }

    @Test
    void shouldRejectCustomerWithCancelledSubscription() throws Exception {

        Subscription subscription = subscribe();
        entitle(true);

        subscription.cancel(Instant.now());
        subscriptionRepository.saveAndFlush(subscription);

        recordUsage("SMS")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NO_ACTIVE_SUBSCRIPTION"));
    }

    @Test
    void shouldRejectProductMissingFromPlan() throws Exception {

        subscribe();

        recordUsage("SMS")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_ENTITLED"));
    }

    @Test
    void shouldRejectProductDisallowedByPlan() throws Exception {

        subscribe();
        entitle(false);

        recordUsage("SMS")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_ENTITLED"));
    }

    @Test
    void shouldKeepServingSubscribersAfterPlanIsDeactivated() throws Exception {

        subscribe();
        entitle(true);

        plan.deactivate(Instant.now());
        planRepository.saveAndFlush(plan);

        recordUsage("SMS")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditCost").value(SMS_CREDIT_COST));
    }

    @Test
    void shouldRejectUsageRequestWithoutApiKey() throws Exception {

        mockMvc.perform(
                        post("/v1/usage")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "productCode": "SMS"
                                        }
                                        """)
                )
                .andExpect(status().isUnauthorized());
    }

    private Subscription subscribe() {
        Instant now = Instant.now();
        return subscriptionRepository.saveAndFlush(
                new Subscription(
                        customer.getId(),
                        plan.getId(),
                        now,
                        now,
                        now.plus(30, ChronoUnit.DAYS)
                )
        );
    }

    private void entitle(boolean allowed) {
        planEntitlementRepository.saveAndFlush(
                new PlanEntitlement(plan, sms, allowed, SMS_CREDIT_COST)
        );
    }

    private ResultActions recordUsage(String productCode) throws Exception {
        return mockMvc.perform(
                post("/v1/usage")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "%s"
                                }
                                """.formatted(productCode))
        );
    }
}
