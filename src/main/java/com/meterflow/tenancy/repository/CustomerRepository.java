package com.meterflow.tenancy.repository;

import com.meterflow.tenancy.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
}
