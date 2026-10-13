package com.meterflow.credential.repository;

import com.meterflow.credential.domain.ApiKey;
import com.meterflow.credential.domain.ApiKeyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey,Long> {
    Optional<ApiKey> findByKeyHash(String keyHash);

    List<ApiKey> findByCustomerId(Long customerId);

    List<ApiKey> findByCustomerIdAndStatus(
            Long customerId,
            ApiKeyStatus status
    );
}
