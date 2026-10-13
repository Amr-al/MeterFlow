package com.meterflow.credential.api;

import com.meterflow.credential.service.ApiKeyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/customers/{customerId}/api-keys")
public class ApiKeyAdminController {

    private final ApiKeyService apiKeyService;

    public ApiKeyAdminController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping
    public ResponseEntity<CreateApiKeyResponse> createApiKey(
            @PathVariable Long customerId) {

        CreateApiKeyResponse response =
                apiKeyService.createApiKey(customerId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<ApiKeyResponse>> listApiKeys(
            @PathVariable Long customerId) {

        return ResponseEntity.ok(
                apiKeyService.listApiKeys(customerId)
        );
    }

    @DeleteMapping("/{apiKeyId}")
    public ResponseEntity<ApiKeyResponse> revokeApiKey(
            @PathVariable Long customerId,
            @PathVariable Long apiKeyId) {

        return ResponseEntity.ok(
                apiKeyService.revokeApiKey(customerId, apiKeyId)
        );
    }
}