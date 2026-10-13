package com.meterflow.credential.api;

import com.meterflow.credential.domain.ApiKey;
import com.meterflow.credential.domain.ApiKeyStatus;

import java.time.Instant;

public record ApiKeyResponse(
        Long id,
        String prefix,
        ApiKeyStatus status,
        Instant createdAt,
        Instant revokedAt
) {
    public static ApiKeyResponse from(ApiKey apiKey) {
        return new ApiKeyResponse(
                apiKey.getId(),
                apiKey.getKeyPrefix(),
                apiKey.getStatus(),
                apiKey.getCreatedAt(),
                apiKey.getRevokedAt()
        );
    }
}