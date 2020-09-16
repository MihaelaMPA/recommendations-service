package com.mpa.microservices.resilient.bookstore.services;

import com.mpa.microservices.resilient.bookstore.clients.OrdersHistoryClient;
import feign.RetryableException;
import feign.jackson.JacksonDecoder;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.feign.FeignDecorators;
import io.github.resilience4j.feign.Resilience4jFeign;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import io.vavr.control.Try;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class RecommendationsService {

    private int counter = 0;

    private RecommendationsServiceFallback recommendationsServiceFallback;
    private OrdersHistoryClient ordersHistoryClient;
    private CircuitBreakerRegistry circuitBreakerRegistry;
    private RateLimiterRegistry rateLimiterRegistry;

    public RecommendationsService(RecommendationsServiceFallback recommendationsServiceFallback,
            OrdersHistoryClient ordersHistoryClient, CircuitBreakerRegistry circuitBreakerRegistry,
            RateLimiterRegistry rateLimiterRegistry) {
        this.recommendationsServiceFallback = recommendationsServiceFallback;
        this.ordersHistoryClient = ordersHistoryClient;
        this.rateLimiterRegistry = rateLimiterRegistry;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    //call order history service directly
    public List<String> getRecommendationsNoCB() {
        return ordersHistoryClient.getOrdersForCB("1").subList(0, 2);
    }

    //call order history service directly
    public List<String> getRecommendationsAnnotationCB() {
        CircuitBreaker annotationCB = circuitBreakerRegistry.circuitBreaker("annotationCB");
        printCircuitBreakerConfigs(annotationCB);
        return ordersHistoryClient.getOrdersForCB("2").subList(0, 2);
    }

    //call order history service with a default config CB and Fallback
    public List<String> getRecommendationsWithFallback() {
        /*
         * Circuit breaker with default configurations:
         * DEFAULT_SLIDING_WINDOW_TYPE = SlidingWindowType.COUNT_BASED --> State change
         * will happen based on count of failed calls.
         * DEFAULT_RECORD_EXCEPTION_PREDICATE --> All exception recorded as failures
         * DEFAULT_MINIMUM_NUMBER_OF_CALLS = 100 DEFAULT_SLIDING_WINDOW_SIZE = 100; -->
         * Minimum number of calls to record before decision to open.
         * DEFAULT_FAILURE_RATE_THRESHOLD = 50% --> Out of recorded calls, if 50% calls
         * are failure, then open circuit breaker
         * DEFAULT_WAIT_DURATION_IN_OPEN_STATE = 60 Seconds --> Wait this much time in
         * open state before moving to half open.
         * DEFAULT_PERMITTED_CALLS_IN_HALF_OPEN_STATE = 10 --> COunt these many calls in
         * half open to decide if circuit breaker can be completely open. *
         */
        CircuitBreaker defaultCB = CircuitBreaker.ofDefaults("default");
        List<String> orders = defaultCB
                .decorateTrySupplier(() -> Try.ofSupplier(() -> ordersHistoryClient.getOrdersForCB("1")))
                .get()
                .recover(RetryableException.class,
                        exception -> recommendationsServiceFallback.getDefaultRecommendations())
                .get();
        printCircuitBreakerConfigs(defaultCB);
        return orders.subList(0, 2);
    }

    public List<String> getRecommendationsWebClient() {
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter("propsRL");
        WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:9091")
                .build();
        Mono<List> listMono = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/ordersHistoryRL")

                        .build())
                .retrieve()
                .bodyToMono(List.class)
                .transform(RateLimiterOperator.of(rateLimiter))
                .onErrorResume(error -> Mono.just(recommendationsServiceFallback.getDefaultRecommendations()));

        return listMono.block(Duration.ofSeconds(1));
    }


    public List<String> getRecommendations() {
        List<String> orderHistory = Collections.emptyList();
        CircuitBreakerConfig countBasedCBConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(2))
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .permittedNumberOfCallsInHalfOpenState(2)
//                .ignoreExceptions(RetryableException.class)
                .build();

        //STATE: CLOSED -> 2 exceptions must be triggered in order to switch from CLOSED -> OPEN
        // (failureRateThreshold = 50%)
        //STATE: OPEN -> after 2 seconds the state is automatically changed from OPEN -> HALF_OPEN
        // (automaticTransitionFromOpenToHalfOpenEnabled = true)
        //STATE: HALF_OPEN; at least 1 calls needed to change from HALF_OPEN -> OPEN (if fails) or HALF_OPEN -> CLOSED
        // (if succeeds).
        // [IMPORTANT: 50% out of 2 calls should fail in order to change from HALF_OPEN -> OPEN]
        CircuitBreaker countBasedCB = CircuitBreaker.of("countBasedCB", countBasedCBConfig);
//        countBasedCB.transitionToDisabledState();
//        countBasedCB.transitionToForcedOpenState();
        printCircuitBreakerConfigs(countBasedCB);
        for (int i = 1; i <= 10; i++) {
            System.out.println("call  = " + i);
            if (i == 4 && countBasedCB.getState().equals(State.FORCED_OPEN)) {
                countBasedCB.reset();
            }
            try {
                // Call service every 1 second.
                Thread.sleep(1000);
                // Decorate service call in circuit breaker
                orderHistory = countBasedCB.executeSupplier(() -> ordersHistoryClient.getOrdersForCB("1"));
                System.out.println(orderHistory);
            } catch (Exception e) {
                System.out.println("----> " + e.getClass().getName());
            } finally {
                printMetrics(countBasedCB);
            }
        }
        return orderHistory.subList(0, 2);
    }

    public List<String> getOrderHistoryRL(String id) {
        counter++;
        System.out.println(Instant.now() + " call " + counter);
        if ("1".equals(id)) {
            RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter("propsRL");
            rateLimiter.changeLimitForPeriod(3);
        }
        return ordersHistoryClient.getOrdersForRL();
    }

    public List<String> getRecommendationsFeignBuilder() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("propsCB");
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter("propsRL");
        FeignDecorators decorators = FeignDecorators.builder()
                .withRateLimiter(rateLimiter)
                .withCircuitBreaker(circuitBreaker)
                .build();
        OrdersHistoryClient ordersHistoryClient = Resilience4jFeign.builder(decorators)
                .contract(new SpringMvcContract())
                .decoder(new JacksonDecoder())
                .target(OrdersHistoryClient.class, "http://localhost:9091/");
        printCircuitBreakerConfigs(circuitBreaker);
        printRateLimiterConfigs(rateLimiter);
        return ordersHistoryClient.getOrdersForRL();
    }

    private void printMetrics(CircuitBreaker circuitBreaker) {
        System.out.println(new StringBuilder().append("Successful calls: ")
                .append(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls()).append(" | Failed calls: ")
                .append(circuitBreaker.getMetrics().getNumberOfFailedCalls()).append(" | Not permitted calls: ")
                .append(circuitBreaker.getMetrics().getNumberOfNotPermittedCalls()).append(" | Failure rate %:")
                .append(circuitBreaker.getMetrics().getFailureRate()).append(" | State: ")
                .append(circuitBreaker.getState()).append("\n").toString());
    }

    private void printCircuitBreakerConfigs(CircuitBreaker circuitBreaker) {
        CircuitBreakerConfig circuitBreakerConfig = circuitBreaker.getCircuitBreakerConfig();
        String cbConfigs = new StringBuilder().append(circuitBreaker.getName()).append(": \n")
                .append("- SLIDING_WINDOW_TYPE: " + circuitBreakerConfig.getSlidingWindowType() + "\n")
                .append("- SLIDING_WINDOW_SIZE: " + circuitBreakerConfig.getSlidingWindowSize() + "\n")
                .append("- MINIMUM_NUMBER_OF_CALLS: " + circuitBreakerConfig.getMinimumNumberOfCalls() + "\n")
                .append("- FAILURE_RATE_THRESHOLD: " + circuitBreakerConfig.getFailureRateThreshold() + "\n")
                .append("- PERMITTED_CALLS_IN_HALF_OPEN_STATE: " + circuitBreakerConfig
                        .getPermittedNumberOfCallsInHalfOpenState())
                .toString();
        System.out.println(cbConfigs);
    }

    private void printMetrics(RateLimiter rateLimiter) {
        System.out.println(new StringBuilder().append("Waiting threads: ")
                .append(rateLimiter.getMetrics().getNumberOfWaitingThreads()).append(" | Available permissions: ")
                .append(rateLimiter.getMetrics().getAvailablePermissions()));
    }

    private void printRateLimiterConfigs(RateLimiter rateLimiter) {
        RateLimiterConfig rateLimiterConfig = rateLimiter.getRateLimiterConfig();
        String cbConfigs = new StringBuilder().append(rateLimiter.getName()).append(": \n")
                .append("- LIMIT_FOR_PERIOD: " + rateLimiterConfig.getLimitForPeriod() + "\n")
                .append("- LIMIT_REFRESH_PERIOD: " + rateLimiterConfig.getLimitRefreshPeriod() + "\n")
                .append("- TIMOUT_DURATION: " + rateLimiterConfig.getTimeoutDuration() + "\n")
                .toString();
        System.out.println(cbConfigs);
    }
}

