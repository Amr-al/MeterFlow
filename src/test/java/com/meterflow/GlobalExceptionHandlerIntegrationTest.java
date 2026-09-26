package com.meterflow;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;


import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GlobalExceptionHandlerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturn404AndCustomerNotFoundErrorCode() throws Exception {

        mockMvc.perform(get("/v0/test"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode")
                        .value("CUSTOMER_NOT_FOUND"));
    }

    @Test
    void shouldReturn500AndInternalErrorCode() throws Exception {

        mockMvc.perform(get("/v0/test-internal"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode")
                        .value("INTERNAL_ERROR"));
    }

    @Test
    void shouldReturn404ForUnknownPath() throws Exception {
        mockMvc.perform(get("/v0/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode")
                        .value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldReturn405ForWrongHttpMethod() throws Exception {
        mockMvc.perform(post("/v0/test"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.errorCode")
                        .value("METHOD_NOT_ALLOWED"));
    }
}
