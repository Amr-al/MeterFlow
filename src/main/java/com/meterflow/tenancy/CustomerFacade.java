package com.meterflow.tenancy;

import com.meterflow.tenancy.api.CustomerView;
import com.meterflow.tenancy.service.CustomerService;
import org.springframework.stereotype.Component;

@Component
public class CustomerFacade {

    private final CustomerService customerService;

    public CustomerFacade(CustomerService customerService) {
        this.customerService = customerService;
    }

    public CustomerView getCustomer(Long id) {
        return customerService.getCustomer(id);
    }
}