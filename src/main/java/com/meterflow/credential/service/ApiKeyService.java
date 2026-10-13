package com.meterflow.credential.service;

import com.meterflow.common.error.ErrorCode;
import com.meterflow.common.error.MeterFlowException;
import com.meterflow.credential.api.ApiKeyResponse;
import com.meterflow.credential.api.CreateApiKeyResponse;
import com.meterflow.credential.domain.ApiKey;
import com.meterflow.credential.domain.ApiKeyStatus;
import com.meterflow.credential.repository.ApiKeyRepository;
import com.meterflow.tenancy.CustomerFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class ApiKeyService {
    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyGenerator apiKeyGenerator;
    private final ApiKeyHasher apiKeyHasher;
    private final CustomerFacade customerFacade;
    private final Clock clock;

    public ApiKeyService(
            ApiKeyRepository apiKeyRepository,
            ApiKeyGenerator apiKeyGenerator,
            ApiKeyHasher apiKeyHasher,
            CustomerFacade customerFacade,
            Clock clock) {

        this.apiKeyRepository = apiKeyRepository;
        this.apiKeyGenerator = apiKeyGenerator;
        this.apiKeyHasher = apiKeyHasher;
        this.customerFacade = customerFacade;
        this.clock = clock;
    }

    @Transactional
    public CreateApiKeyResponse createApiKey(Long customerId) {

        customerFacade.getCustomer(customerId);

        String rawApiKey = apiKeyGenerator.generate();

        String prefix = apiKeyGenerator.extractPrefix(rawApiKey);

        String keyHash = apiKeyHasher.hash(rawApiKey);

        Instant now = Instant.now(clock);

        ApiKey apiKey = new ApiKey(
                customerId,
                prefix,
                keyHash,
                now
        );

        ApiKey savedApiKey = apiKeyRepository.save(apiKey);

        return new CreateApiKeyResponse(
                savedApiKey.getId(),
                rawApiKey,
                savedApiKey.getKeyPrefix(),
                savedApiKey.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> listApiKeys(Long customerId) {

        customerFacade.getCustomer(customerId);

        return apiKeyRepository.findByCustomerId(customerId)
                .stream()
                .map(ApiKeyResponse::from)
                .toList();
    }

    @Transactional
    public ApiKeyResponse revokeApiKey(Long customerId, Long apiKeyId) {

        customerFacade.getCustomer(customerId);

        ApiKey apiKey = apiKeyRepository.findById(apiKeyId)
                .orElseThrow(() -> new MeterFlowException(
                        ErrorCode.API_KEY_NOT_FOUND,
                        "API key not found: " + apiKeyId
                ));

        if (!apiKey.getCustomerId().equals(customerId)) {
            throw new MeterFlowException(
                    ErrorCode.API_KEY_NOT_FOUND,
                    "API key not found: " + apiKeyId
            );
        }

        if (apiKey.getStatus() != ApiKeyStatus.REVOKED) {
            apiKey.revoke(Instant.now(clock));
        }

        return ApiKeyResponse.from(apiKey);
    }

}
