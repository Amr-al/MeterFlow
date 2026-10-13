package com.meterflow.credential.domain;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Entity
@Table(name = "api_key")
@Getter
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "key_prefix", nullable = false)
    private String keyPrefix;

    @Column(name = "key_hash", nullable = false, unique = true)
    private String keyHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApiKeyStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected ApiKey() {
    }

    public ApiKey(
            Long customerId,
            String keyPrefix,
            String keyHash,
            Instant createdAt) {

        this.customerId = customerId;
        this.keyPrefix = keyPrefix;
        this.keyHash = keyHash;
        this.status = ApiKeyStatus.ACTIVE;
        this.createdAt = createdAt;
    }

    public void revoke(Instant revokedAt) {
        this.status = ApiKeyStatus.REVOKED;
        this.revokedAt = revokedAt;
    }

}
