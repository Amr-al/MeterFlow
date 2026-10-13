package com.meterflow.security;

import com.meterflow.credential.api.CreateApiKeyResponse;
import com.meterflow.credential.repository.ApiKeyRepository;
import com.meterflow.credential.service.ApiKeyService;
import com.meterflow.support.IntegrationTest;
import com.meterflow.tenancy.domain.Customer;
import com.meterflow.tenancy.repository.CustomerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiKeySecurityIntegrationTest extends IntegrationTest {

    private static final String API_KEY_HEADER = "X-API-Key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private ApiKeyService apiKeyService;

    @BeforeEach
    void setUp() {
        cleanup();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        apiKeyRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();
    }

    @Test
    void shouldReturn401WhenApiKeyIsMissing() throws Exception {

        mockMvc.perform(
                        get("/v1/ping")
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn401WhenApiKeyIsInvalid() throws Exception {

        mockMvc.perform(
                        get("/v1/ping")
                                .header(
                                        API_KEY_HEADER,
                                        "mf_live_invalid-api-key"
                                )
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAuthenticateUsingActiveApiKey() throws Exception {

        Customer customer = createCustomer("Amr");

        CreateApiKeyResponse apiKeyResponse =
                apiKeyService.createApiKey(customer.getId());

        mockMvc.perform(
                        get("/v1/ping")
                                .header(
                                        API_KEY_HEADER,
                                        apiKeyResponse.apiKey()
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.customerId")
                                .value(customer.getId())
                );
    }

    @Test
    void shouldNotLeakAuthenticationBetweenRequests() throws Exception {

        Customer customer = customerRepository.saveAndFlush(
                new Customer("Customer A", Instant.now())
        );

        CreateApiKeyResponse apiKey =
                apiKeyService.createApiKey(customer.getId());

        mockMvc.perform(
                        get("/v1/ping")
                                .header("X-API-Key", apiKey.apiKey())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId")
                        .value(customer.getId()));

        mockMvc.perform(
                        get("/v1/ping")
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn401WhenApiKeyIsRevoked() throws Exception {

        Customer customer = createCustomer("Amr");

        CreateApiKeyResponse apiKeyResponse =
                apiKeyService.createApiKey(customer.getId());

        apiKeyService.revokeApiKey(
                customer.getId(),
                apiKeyResponse.id()
        );

        mockMvc.perform(
                        get("/v1/ping")
                                .header(
                                        API_KEY_HEADER,
                                        apiKeyResponse.apiKey()
                                )
                )
                .andExpect(status().isUnauthorized());
    }

    private Customer createCustomer(String name) {

        Customer customer =
                new Customer(
                        name,
                        Instant.now()
                );

        return customerRepository.saveAndFlush(customer);
    }
}