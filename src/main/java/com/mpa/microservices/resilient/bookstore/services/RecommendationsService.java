package com.mpa.microservices.resilient.bookstore.services;

import com.mpa.microservices.resilient.bookstore.clients.OrdersHistoryClient;
import feign.RetryableException;
import feign.codec.Decoder;
import feign.codec.Decoder.Default;
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
import io.vavr.control.Try;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.openfeign.support.ResponseEntityDecoder;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.stereotype.Service;

@Service
public class RecommendationsService {

    @Autowired
    CircuitBreakerRegistry circuitBreakerRegistry;

    private RecommendationsServiceFallback recommendationsServiceFallback;
    private OrdersHistoryClient ordersHistoryClient;

    public RecommendationsService(RecommendationsServiceFallback recommendationsServiceFallback,
            OrdersHistoryClient ordersHistoryClient) {
        this.recommendationsServiceFallback = recommendationsServiceFallback;
        this.ordersHistoryClient = ordersHistoryClient;
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

    //default from props is actually a CircuitBreakerConfig and instance = circuitbreaker
    public List<String> getRecommendationsFromProps() {
        CircuitBreaker propsCB = circuitBreakerRegistry.circuitBreaker("propsCB");
        printCircuitBreakerConfigs(propsCB);
        List<String> orders = ordersHistoryClient.getOrdersForCB("1");
        return orders;
    }


    public List<String> getRecommendations() {
        List<String> orderHistory = Collections.emptyList();
        CircuitBreakerConfig countBasedCBConfig = CircuitBreakerConfig.custom()
                //the size of the sliding window which is used to record the outcome of calls when the CircuitBreaker is closed.
                .slidingWindowSize(4)
                //When the failure rate is equal or greater than the threshold the CircuitBreaker transitions to open
                .failureRateThreshold(50)
                //The time that the CircuitBreaker should wait before transitioning from open to half-open.
                .waitDurationInOpenState(Duration.ofSeconds(2))
                //CircuitBreaker will automatically transition from open to half-open state and no call is needed
                //to trigger the transition.
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                //the number of permitted calls when the CircuitBreaker is half open.
                .permittedNumberOfCallsInHalfOpenState(2)
//                .ignoreExceptions(CallUnsuccessful.class)
                .build();
        //inregistreaza 4 calluri ianinte de a lua decizia de OPEN (de a continua logica de mai jos
        //daca 50% din calluri failuie (2)  -> OPEN
        //asteapta 2s in OPEN inainte de a trece in HALF-OPEN
        //numara 2 calluri in HALF-OPEN inainte de a decide daca treci in CLOSE sau OPEN

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
            System.out.println("counter = " + i);
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

    //1 request/1 second. Any other requests will wait for 1 s before throwing exception
    public void callOrderHistoryWithRateLimiter() throws InterruptedException {
        RateLimiterConfig config = RateLimiterConfig.custom()
                //allow 1 calls ->  1 active permissions
                .limitForPeriod(1)
                //every 1 seconds -> 1s for a cycle
                .limitRefreshPeriod(Duration.ofSeconds(1))
                //keep other calls waiting until maximum of 250ms -> throws RequestNotPermitted
                .timeoutDuration(Duration.ofMillis(250))
                .build();

        RateLimiterRegistry rateLimiterRegistry = RateLimiterRegistry.of(config);
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter("orderHistoryRL");

        // Make 4 calls using service client mimicking 4 parallel users.
        for (int i = 0; i < 4; i++) {
            System.out.println(LocalTime.now() + "call-" + (i + 1));
            new Thread(() -> {
                Runnable runnable = () -> ordersHistoryClient.getOrdersForRL();
                rateLimiter.executeRunnable(runnable);
            }, "call-" + (i + 1)).start();
            printMetrics(rateLimiter);
            Thread.sleep(50);
        }

    }

    public List<String> getOrderHistoryRL() {
        return ordersHistoryClient.getOrdersForRL();
    }

    public List<String> getRecommendationsFeignBuilder() {
        CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("defaultCB");
        RateLimiter rateLimiter = RateLimiter.ofDefaults("defaultRL");
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

