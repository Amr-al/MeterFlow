package com.meterflow.subscription.api;

import com.meterflow.subscription.service.SubscriptionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/customers/{customerId}/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(
            SubscriptionService subscriptionService) {

        this.subscriptionService = subscriptionService;
    }

    @PostMapping
    public ResponseEntity<SubscriptionResponse> subscribe(
            @PathVariable Long customerId,
            @Valid @RequestBody CreateSubscriptionRequest request) {

        SubscriptionResponse response =
                subscriptionService.subscribe(
                        customerId,
                        request.planCode()
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/active")
    public ResponseEntity<SubscriptionResponse> getActiveSubscription(
            @PathVariable Long customerId) {

        SubscriptionResponse response =
                subscriptionService.getActiveSubscription(customerId);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/active/cancel")
    public ResponseEntity<SubscriptionResponse> cancelActiveSubscription(
            @PathVariable Long customerId) {

        SubscriptionResponse response =
                subscriptionService.cancelActiveSubscription(customerId);

        return ResponseEntity.ok(response);
    }
}