package com.meterflow.tenancy.api;

import com.meterflow.tenancy.domain.Customer;
import com.meterflow.tenancy.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/customers")
public class CustomerController {
    private final CustomerService customerService;

    public CustomerController(CustomerService customerService){
        this.customerService = customerService;
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> create(
            @Valid @RequestBody CreateCustomerRequest request){

        CustomerView customer = customerService.createCustomer(request.name());

        CustomerResponse response = new CustomerResponse(
                customer.id(),
                customer.name(),
                customer.status()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);

    }

    @GetMapping("/{id}")
    public CustomerResponse getById(@PathVariable Long id) {

        CustomerView customer = customerService.getCustomer(id);

        return new CustomerResponse(
                customer.id(),
                customer.name(),
                customer.status()
        );
    }

    @GetMapping
    public List<Customer> getAll(){
        return customerService.getAll();
    }

}
