package com.meterflow.gateway.api;

import com.meterflow.gateway.service.MeteringService;
import com.meterflow.gateway.service.UsageAuthorization;
import com.meterflow.security.CurrentCustomer;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1")
public class MeteringController {

    private final MeteringService meteringService;
    private final CurrentCustomer currentCustomer;

    public MeteringController(
            MeteringService meteringService,
            CurrentCustomer currentCustomer
    ) {
        this.meteringService = meteringService;
        this.currentCustomer = currentCustomer;
    }

    @PostMapping("/usage")
    public ResponseEntity<MeteredResponse> recordUsage(
            @Valid @RequestBody MeteredRequest request
    ) {

        Long customerId = currentCustomer.getCustomerId();

        UsageAuthorization authorization =
                meteringService.authorize(
                        customerId,
                        request.productCode()
                );

        return ResponseEntity.ok(MeteredResponse.from(authorization));
    }
}