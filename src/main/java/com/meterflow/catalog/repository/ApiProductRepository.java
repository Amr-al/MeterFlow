package com.meterflow.catalog.repository;

import com.meterflow.catalog.domain.ApiProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApiProductRepository extends JpaRepository<ApiProduct, Long> {

    Optional<ApiProduct> findByCode(String code);
    boolean existsByCode(String code);
}
