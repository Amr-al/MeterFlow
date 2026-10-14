package com.meterflow.security;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;


@RestController
@RequestMapping("/v1")
public class DataPlaneTestController {

    @GetMapping("/ping")
    public Map<String, Object> ping(
            Authentication authentication
    ) {

        return Map.of(
                "customerId",
                authentication.getPrincipal()
        );
    }
}