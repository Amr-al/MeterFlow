package com.meterflow.credential.api;

import java.time.Instant;

public record CreateApiKeyResponse(
        Long id,
        String apiKey,
        String prefix,
        Instant createdAt
) {
}