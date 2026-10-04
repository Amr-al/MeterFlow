package com.meterflow.tenancy.service;

import com.meterflow.common.error.ErrorCode;
import com.meterflow.common.error.MeterFlowException;
import com.meterflow.tenancy.api.CustomerView;
import com.meterflow.tenancy.domain.Customer;
import com.meterflow.tenancy.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final Clock clock;

    public CustomerService(
            CustomerRepository customerRepository,
            Clock clock) {

        this.customerRepository = customerRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CustomerView getCustomer(Long id) {

        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new MeterFlowException(
                        ErrorCode.CUSTOMER_NOT_FOUND,
                        "Customer not found: " + id
                ));

        return new CustomerView(
                customer.getId(),
                customer.getName(),
                customer.getStatus()
        );
    }

    @Transactional
    public CustomerView createCustomer(String name){

        Instant now = Instant.now(clock);

        Customer savedCustomer = new Customer(name, now);

        customerRepository.save(savedCustomer);

        return new CustomerView(savedCustomer.getId(),
                savedCustomer.getName(),
                savedCustomer.getStatus());
    }

    public List<Customer> getAll(){
        return customerRepository.findAll();
    }
}