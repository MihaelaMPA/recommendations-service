package com.mpa.microservices.resilient.bookstore.services;

import com.mpa.microservices.resilient.bookstore.clients.OrdersHistoryClient;
import feign.RetryableException;
import feign.jackson.JacksonDecoder;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.feign.FeignDecorators;
import io.github.resilience4j.feign.Resilience4jFeign;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.vavr.control.Try;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.stereotype.Service;

@Service
public class CircuitBreakerRecommendationsService {

    private RecommendationsServiceFallback recommendationsServiceFallback;
    private OrdersHistoryClient ordersHistoryClient;
    private CircuitBreakerRegistry circuitBreakerRegistry;
    private CircuitBreakerConfig circuitBreakerConfig;
    private RateLimiterRegistry rateLimiterRegistry;

    public CircuitBreakerRecommendationsService(RecommendationsServiceFallback recommendationsServiceFallback,
            OrdersHistoryClient ordersHistoryClient, CircuitBreakerRegistry circuitBreakerRegistry,
            RateLimiterRegistry rateLimiterRegistry) {
        this.recommendationsServiceFallback = recommendationsServiceFallback;
        this.ordersHistoryClient = ordersHistoryClient;
        this.rateLimiterRegistry = rateLimiterRegistry;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    public List<String> getRecommendationsNoCB() {
        return ordersHistoryClient.getOrdersForCB().subList(0, 2);
    }

    public List<String> getRecommendationsAnnotationCB() {
        CircuitBreaker annotationCB = circuitBreakerRegistry.circuitBreaker("annotationCB");
        printCircuitBreakerConfigs(annotationCB);
        return ordersHistoryClient.getOrdersException().subList(0, 2);
    }

    //call order history service with a default config CB and fallback
    public List<String> getRecommendationsWithFallback() {
        CircuitBreaker defaultCB = CircuitBreaker.ofDefaults("default");
        List<String> orders = defaultCB
                //decorate and execute Supplier
                //handle exceptions in a functional way with Try from Vavr-> https://www.baeldung.com/vavr-try

                //we are not obliged to use try, as a matter of a fact you will see the
                //try catch block for the same call
                // you can think of Try like Optional, but in case of exception it is not empty, it contains the
                //exception. To avoid
                .executeTrySupplier(() -> Try.of(() -> ordersHistoryClient.getOrdersForCB()))
                //fallback (we can even fallback from CallNotPermittedException)
                .recover(RetryableException.class,
                        exception -> recommendationsServiceFallback.getDefaultRecommendations())
                .recover(CallNotPermittedException.class,
                        exception -> recommendationsServiceFallback.getDefaultRecommendations())
                .get();
        printCircuitBreakerConfigs(defaultCB);
        return orders.subList(0, 2);
    }

    public List<String> getRecommendations() {
        List<String> orderHistory = Collections.emptyList();
        CircuitBreakerConfig countBasedCBConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(2))
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .permittedNumberOfCallsInHalfOpenState(2)
//                .ignoreExceptions(CallUnsuccessful.class, ExceptionInInitializerError.class)
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
                // Decorate service call with CB and execute
                orderHistory = countBasedCB.executeSupplier(() -> ordersHistoryClient.getOrdersForCB());
                System.out.println(orderHistory);
            } catch (Exception e) {
                System.out.println("----> " + e.getClass().getName());
            } finally {
                printMetrics(countBasedCB);
            }
        }
        return orderHistory.subList(0, 2);
    }

    public void replaceCB() {
        System.out.println("BEFORE: circuit breakers:" + circuitBreakerRegistry.getAllCircuitBreakers());

        CircuitBreaker newCircuitBreaker = CircuitBreaker.ofDefaults("newCB");
        circuitBreakerRegistry.replace("oldCB", newCircuitBreaker);

        System.out.println("AFTER: circuit breakers:" + circuitBreakerRegistry.getAllCircuitBreakers());
    }

    public List<String> getRecommendationsFeignBuilder() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("propsCB");
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter("propsRL");
        //The order in which decorators are applied correspond to the order in which they are declared.
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
