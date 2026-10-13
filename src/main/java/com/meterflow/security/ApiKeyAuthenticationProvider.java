package com.meterflow.security;

import com.meterflow.credential.domain.ApiKey;
import com.meterflow.credential.domain.ApiKeyStatus;
import com.meterflow.credential.repository.ApiKeyRepository;
import com.meterflow.credential.service.ApiKeyHasher;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

@Component
public class ApiKeyAuthenticationProvider
        implements AuthenticationProvider {

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyHasher apiKeyHasher;

    public ApiKeyAuthenticationProvider(
            ApiKeyRepository apiKeyRepository,
            ApiKeyHasher apiKeyHasher
    ) {
        this.apiKeyRepository = apiKeyRepository;
        this.apiKeyHasher = apiKeyHasher;
    }

    @Override
    public Authentication authenticate(
            Authentication authentication
    ) throws AuthenticationException {

        String rawApiKey =
                (String) authentication.getCredentials();

        if (rawApiKey == null || rawApiKey.isBlank()) {
            throw new BadCredentialsException(
                    "Invalid API key"
            );
        }

        String keyHash =
                apiKeyHasher.hash(rawApiKey);

        ApiKey apiKey = apiKeyRepository
                .findByKeyHash(keyHash)
                .orElseThrow(() ->
                        new BadCredentialsException(
                                "Invalid API key"
                        )
                );

        if (apiKey.getStatus() != ApiKeyStatus.ACTIVE) {
            throw new BadCredentialsException(
                    "Invalid API key"
            );
        }

        return ApiKeyAuthenticationToken.authenticated(
                apiKey.getCustomerId()
        );
    }

    @Override
    public boolean supports(
            Class<?> authentication
    ) {
        return ApiKeyAuthenticationToken.class
                .isAssignableFrom(authentication);
    }
}