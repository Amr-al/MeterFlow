package com.meterflow.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    private final Long customerId;
    private String apiKey;

    private ApiKeyAuthenticationToken(
            String apiKey,
            Long customerId,
            boolean authenticated
    ) {
        super(
                authenticated
                        ? List.of(new SimpleGrantedAuthority("ROLE_API_CLIENT"))
                        : List.of()
        );

        this.apiKey = apiKey;
        this.customerId = customerId;

        super.setAuthenticated(authenticated);
    }

    public static ApiKeyAuthenticationToken unauthenticated(
            String apiKey
    ) {
        return new ApiKeyAuthenticationToken(
                apiKey,
                null,
                false
        );
    }

    public static ApiKeyAuthenticationToken authenticated(
            Long customerId
    ) {
        return new ApiKeyAuthenticationToken(
                null,
                customerId,
                true
        );
    }

    @Override
    public Object getCredentials() {
        return apiKey;
    }

    @Override
    public Object getPrincipal() {
        return customerId;
    }

    @Override
    public void eraseCredentials() {
        super.eraseCredentials();
        this.apiKey = null;
    }
}