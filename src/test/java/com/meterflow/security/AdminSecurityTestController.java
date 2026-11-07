package com.meterflow.security;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminSecurityTestController {

    @GetMapping("/ping")
    public String ping() {
        return "admin";
    }
}