package com.meterflow.credential;

import com.jayway.jsonpath.JsonPath;
import com.meterflow.credential.domain.ApiKey;
import com.meterflow.credential.domain.ApiKeyStatus;
import com.meterflow.credential.repository.ApiKeyRepository;
import com.meterflow.credential.service.ApiKeyHasher;
import com.meterflow.support.IntegrationTest;
import com.meterflow.tenancy.domain.Customer;
import com.meterflow.tenancy.repository.CustomerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiKeyAdminControllerIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private ApiKeyHasher apiKeyHasher;

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
    void shouldCreateApiKey() throws Exception {

        Customer customer = createCustomer("Amr");

        mockMvc.perform(
                        post("/admin/customers/{customerId}/api-keys",
                                customer.getId())
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.apiKey").exists())
                .andExpect(jsonPath("$.apiKey").isNotEmpty())
                .andExpect(jsonPath("$.prefix").exists())
                .andExpect(jsonPath("$.createdAt").exists());

        List<ApiKey> storedKeys = apiKeyRepository.findAll();

        assertEquals(1, storedKeys.size());

        ApiKey storedKey = storedKeys.get(0);

        assertEquals(customer.getId(), storedKey.getCustomerId());
        assertEquals(ApiKeyStatus.ACTIVE, storedKey.getStatus());
        assertNotNull(storedKey.getKeyHash());
        assertNotNull(storedKey.getKeyPrefix());
        assertNotNull(storedKey.getCreatedAt());
        assertNull(storedKey.getRevokedAt());
    }

    @Test
    void shouldGenerateApiKeyWithMeterFlowPrefix() throws Exception {

        Customer customer = createCustomer("Amr");

        mockMvc.perform(
                        post("/admin/customers/{customerId}/api-keys",
                                customer.getId())
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.apiKey").value(
                        org.hamcrest.Matchers.startsWith("mf_live_")
                ));
    }

    @Test
    void shouldNeverStoreRawApiKey() throws Exception {

        Customer customer = createCustomer("Amr");

        String responseBody = mockMvc.perform(
                        post("/admin/customers/{customerId}/api-keys",
                                customer.getId())
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String rawApiKey = JsonPath.read(
                responseBody,
                "$.apiKey"
        );

        ApiKey storedKey = apiKeyRepository.findAll().get(0);

        assertNotEquals(
                rawApiKey,
                storedKey.getKeyHash()
        );

        assertEquals(
                apiKeyHasher.hash(rawApiKey),
                storedKey.getKeyHash()
        );
    }

    @Test
    void shouldStoreOnlyPrefixForIdentification() throws Exception {

        Customer customer = createCustomer("Amr");

        String responseBody = mockMvc.perform(
                        post("/admin/customers/{customerId}/api-keys",
                                customer.getId())
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String rawApiKey = JsonPath.read(
                responseBody,
                "$.apiKey"
        );

        String returnedPrefix = JsonPath.read(
                responseBody,
                "$.prefix"
        );

        ApiKey storedKey = apiKeyRepository.findAll().get(0);

        assertTrue(rawApiKey.startsWith(returnedPrefix));

        assertEquals(
                returnedPrefix,
                storedKey.getKeyPrefix()
        );

        assertNotEquals(
                rawApiKey,
                storedKey.getKeyPrefix()
        );
    }

    @Test
    void shouldReturn404WhenCreatingApiKeyForUnknownCustomer()
            throws Exception {

        mockMvc.perform(
                        post("/admin/customers/{customerId}/api-keys",
                                999999L)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode")
                        .value("CUSTOMER_NOT_FOUND"));

        assertEquals(
                0,
                apiKeyRepository.count()
        );
    }

    @Test
    void shouldListCustomerApiKeys() throws Exception {

        Customer customer = createCustomer("Amr");

        createApiKey(customer.getId());
        createApiKey(customer.getId());

        mockMvc.perform(
                        get("/admin/customers/{customerId}/api-keys",
                                customer.getId())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].prefix").exists())
                .andExpect(jsonPath("$[0].status")
                        .value("ACTIVE"))
                .andExpect(jsonPath("$[0].createdAt").exists());
    }

    @Test
    void listShouldNotExposeRawApiKeyOrHash() throws Exception {

        Customer customer = createCustomer("Amr");

        createApiKey(customer.getId());

        mockMvc.perform(
                        get("/admin/customers/{customerId}/api-keys",
                                customer.getId())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].prefix").exists())
                .andExpect(jsonPath("$[0].status")
                        .value("ACTIVE"))

                // secret must never appear again
                .andExpect(jsonPath("$[0].apiKey")
                        .doesNotExist())

                // stored hash is internal too
                .andExpect(jsonPath("$[0].keyHash")
                        .doesNotExist());
    }

    @Test
    void shouldReturnOnlyKeysBelongingToCustomer()
            throws Exception {

        Customer firstCustomer =
                createCustomer("Customer One");

        Customer secondCustomer =
                createCustomer("Customer Two");

        createApiKey(firstCustomer.getId());
        createApiKey(firstCustomer.getId());

        createApiKey(secondCustomer.getId());

        mockMvc.perform(
                        get("/admin/customers/{customerId}/api-keys",
                                firstCustomer.getId())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(
                        get("/admin/customers/{customerId}/api-keys",
                                secondCustomer.getId())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void shouldRevokeApiKey() throws Exception {

        Customer customer = createCustomer("Amr");

        Long apiKeyId =
                createApiKeyAndReturnId(customer.getId());

        mockMvc.perform(
                        delete(
                                "/admin/customers/{customerId}/api-keys/{apiKeyId}",
                                customer.getId(),
                                apiKeyId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id")
                        .value(apiKeyId))
                .andExpect(jsonPath("$.status")
                        .value("REVOKED"))
                .andExpect(jsonPath("$.revokedAt")
                        .exists());

        ApiKey storedKey =
                apiKeyRepository.findById(apiKeyId)
                        .orElseThrow();

        assertEquals(
                ApiKeyStatus.REVOKED,
                storedKey.getStatus()
        );

        assertNotNull(
                storedKey.getRevokedAt()
        );
    }

    @Test
    void shouldNotAllowCustomerToRevokeAnotherCustomersApiKey()
            throws Exception {

        Customer owner =
                createCustomer("Owner");

        Customer otherCustomer =
                createCustomer("Other Customer");

        Long apiKeyId =
                createApiKeyAndReturnId(owner.getId());

        mockMvc.perform(
                        delete(
                                "/admin/customers/{customerId}/api-keys/{apiKeyId}",
                                otherCustomer.getId(),
                                apiKeyId
                        )
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode")
                        .value("API_KEY_NOT_FOUND"));

        ApiKey storedKey =
                apiKeyRepository.findById(apiKeyId)
                        .orElseThrow();

        assertEquals(
                ApiKeyStatus.ACTIVE,
                storedKey.getStatus()
        );

        assertNull(
                storedKey.getRevokedAt()
        );
    }

    @Test
    void shouldReturn404WhenRevokingUnknownApiKey()
            throws Exception {

        Customer customer = createCustomer("Amr");

        mockMvc.perform(
                        delete(
                                "/admin/customers/{customerId}/api-keys/{apiKeyId}",
                                customer.getId(),
                                999999L
                        )
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode")
                        .value("API_KEY_NOT_FOUND"));
    }

    @Test
    void shouldAllowRevokeToBeCalledTwice()
            throws Exception {

        Customer customer = createCustomer("Amr");

        Long apiKeyId =
                createApiKeyAndReturnId(customer.getId());

        mockMvc.perform(
                        delete(
                                "/admin/customers/{customerId}/api-keys/{apiKeyId}",
                                customer.getId(),
                                apiKeyId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status")
                        .value("REVOKED"));

        Instant firstRevokedAt =
                apiKeyRepository.findById(apiKeyId)
                        .orElseThrow()
                        .getRevokedAt();

        mockMvc.perform(
                        delete(
                                "/admin/customers/{customerId}/api-keys/{apiKeyId}",
                                customer.getId(),
                                apiKeyId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status")
                        .value("REVOKED"));

        ApiKey storedKey =
                apiKeyRepository.findById(apiKeyId)
                        .orElseThrow();

        assertEquals(
                ApiKeyStatus.REVOKED,
                storedKey.getStatus()
        );

        assertEquals(
                firstRevokedAt,
                storedKey.getRevokedAt()
        );

        assertEquals(
                1,
                apiKeyRepository.count()
        );
    }

    @Test
    void shouldReturn404WhenListingKeysForUnknownCustomer()
            throws Exception {

        mockMvc.perform(
                        get("/admin/customers/{customerId}/api-keys",
                                999999L)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode")
                        .value("CUSTOMER_NOT_FOUND"));
    }

    private Customer createCustomer(String name) {

        Customer customer =
                new Customer(
                        name,
                        Instant.now()
                );

        return customerRepository.saveAndFlush(customer);
    }

    private void createApiKey(Long customerId)
            throws Exception {

        mockMvc.perform(
                        post("/admin/customers/{customerId}/api-keys",
                                customerId)
                )
                .andExpect(status().isCreated());
    }

    private Long createApiKeyAndReturnId(Long customerId)
            throws Exception {

        String responseBody = mockMvc.perform(
                        post("/admin/customers/{customerId}/api-keys",
                                customerId)
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number id = JsonPath.read(
                responseBody,
                "$.id"
        );

        return id.longValue();
    }
}