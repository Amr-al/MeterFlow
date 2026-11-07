package com.meterflow.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentCustomer {

    public Long getCustomerId() {

       Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

       if(authentication == null || !authentication.isAuthenticated() ||
                !(authentication.getPrincipal() instanceof Long customerId)) {
           throw new IllegalStateException(
                   "Authenticated customer is required"
           );
       }
       return customerId ;
    }
}