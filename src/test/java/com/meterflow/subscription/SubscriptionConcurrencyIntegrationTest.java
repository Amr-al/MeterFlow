package com.meterflow.subscription;

import com.meterflow.catalog.domain.Plan;
import com.meterflow.catalog.repository.PlanRepository;
import com.meterflow.common.error.ErrorCode;
import com.meterflow.common.error.MeterFlowException;
import com.meterflow.subscription.api.SubscriptionResponse;
import com.meterflow.subscription.domain.SubscriptionStatus;
import com.meterflow.subscription.repository.SubscriptionRepository;
import com.meterflow.subscription.service.SubscriptionService;
import com.meterflow.tenancy.domain.Customer;
import com.meterflow.tenancy.repository.CustomerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
@SpringBootTest
@ActiveProfiles("test")
class SubscriptionConcurrencyIntegrationTest {

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PlanRepository planRepository;

    @BeforeEach
    void setUp() {
        cleanup();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        subscriptionRepository.deleteAllInBatch();
        planRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();
    }

    @Test
    void shouldAllowOnlyOneActiveSubscriptionUnderConcurrency()
            throws Exception {

        Customer customer = customerRepository.saveAndFlush(
                new Customer(
                        "Amr",
                        Instant.now()
                )
        );

        planRepository.saveAndFlush(
                new Plan(
                        "STARTER",
                        "Starter Plan",
                        100,
                        100000L,
                        Instant.now()
                )
        );

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        try {
            CountDownLatch startGate =
                    new CountDownLatch(1);

            Callable<Object> subscribeTask = () -> {

                startGate.await();

                try {
                    return subscriptionService.subscribe(
                            customer.getId(),
                            "STARTER"
                    );
                } catch (Exception ex) {
                    return ex;
                }
            };

            Future<Object> first =
                    executor.submit(subscribeTask);

            Future<Object> second =
                    executor.submit(subscribeTask);

            startGate.countDown();

            Object result1 = first.get();
            Object result2 = second.get();

            System.out.println("result1 type = "
                    + result1.getClass().getName());
            System.out.println("result2 type = "
                    + result2.getClass().getName());

            long successes =
                    Stream.of(result1, result2)
                            .filter(SubscriptionResponse.class::isInstance)
                            .count();

            long conflicts =
                    Stream.of(result1, result2)
                            .filter(MeterFlowException.class::isInstance)
                            .map(MeterFlowException.class::cast)
                            .filter(ex ->
                                    ex.getErrorCode()
                                            == ErrorCode.SUBSCRIPTION_ALREADY_ACTIVE)
                            .count();

            assertEquals(1, successes);
            assertEquals(1, conflicts);

            long activeSubscriptions =
                    subscriptionRepository.findAll()
                            .stream()
                            .filter(subscription ->
                                    subscription.getStatus()
                                            == SubscriptionStatus.ACTIVE)
                            .count();

            assertEquals(1, activeSubscriptions);

        } finally {
            executor.shutdownNow();
        }
    }
}