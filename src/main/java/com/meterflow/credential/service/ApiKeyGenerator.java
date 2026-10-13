package com.meterflow.credential.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class ApiKeyGenerator {

    private static final String PREFIX = "mf_live_";
    private static final int RANDOM_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {

        byte[] randomBytes = new byte[RANDOM_BYTES];
        secureRandom.nextBytes(randomBytes);

        String secret = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        return PREFIX + secret;
    }

    public String extractPrefix(String rawApiKey) {
        return rawApiKey.substring(
                0,
                Math.min(16, rawApiKey.length())
        );
    }
}