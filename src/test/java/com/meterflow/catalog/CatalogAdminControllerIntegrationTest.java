package com.meterflow.catalog;

import com.meterflow.catalog.domain.ApiProduct;
import com.meterflow.catalog.repository.ApiProductRepository;
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
class CatalogAdminControllerIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiProductRepository apiProductRepository;

    @BeforeEach
    void setUp() {
        apiProductRepository.deleteAll();
    }

    @Test
    void shouldCreateApiProduct() throws Exception {

        mockMvc.perform(post("/admin/api-products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "SMS",
                                  "displayName": "SMS API"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.code").value("SMS"))
                .andExpect(jsonPath("$.displayName").value("SMS API"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void shouldReturn400WhenCreatingProductWithBlankCode() throws Exception {

        mockMvc.perform(post("/admin/api-products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "",
                                  "displayName": "SMS API"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("VALIDATION_FAILED"));
    }

    @Test
    void shouldReturn400WhenCreatingProductWithBlankDisplayName()
            throws Exception {

        mockMvc.perform(post("/admin/api-products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "SMS",
                                  "displayName": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("VALIDATION_FAILED"));
    }

    @Test
    void shouldReturn409WhenProductCodeAlreadyExists()
            throws Exception {

        mockMvc.perform(post("/admin/api-products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "SMS",
                                  "displayName": "SMS API"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/admin/api-products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "sms",
                                  "displayName": "Another SMS API"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode")
                        .value("DUPLICATE_RESOURCE"));
    }

    @Test
    void shouldReturnApiProductsByPage() throws Exception {

        saveProduct("SMS", "SMS API");
        saveProduct("OTP", "OTP API");

        mockMvc.perform(get("/admin/api-products")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void shouldReturn400WhenPageIsNegative() throws Exception {

        mockMvc.perform(get("/admin/api-products")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldDeactivateApiProduct() throws Exception {

        saveProduct("SMS", "SMS API");

        mockMvc.perform(patch("/admin/api-products/SMS")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "active": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SMS"))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void shouldActivateApiProduct() throws Exception {

        ApiProduct product = saveProduct("SMS", "SMS API");

        product.deactivate(Instant.now());
        apiProductRepository.saveAndFlush(product);

        mockMvc.perform(patch("/admin/api-products/SMS")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "active": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SMS"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void shouldReturn404WhenUpdatingUnknownProduct() throws Exception {

        mockMvc.perform(patch("/admin/api-products/UNKNOWN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "active": false
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode")
                        .value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void shouldReturn400WhenActiveIsMissing() throws Exception {

        saveProduct("SMS", "SMS API");

        mockMvc.perform(patch("/admin/api-products/SMS")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("VALIDATION_FAILED"));
    }

    private ApiProduct saveProduct(
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