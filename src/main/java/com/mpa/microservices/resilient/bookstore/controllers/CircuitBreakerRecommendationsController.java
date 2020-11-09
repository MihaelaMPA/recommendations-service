package com.mpa.microservices.resilient.bookstore.controllers;

import com.mpa.microservices.resilient.bookstore.exceptions.CallUnsuccessful;
import com.mpa.microservices.resilient.bookstore.services.CircuitBreakerRecommendationsService;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/recommendations")
public class CircuitBreakerRecommendationsController {

    private CircuitBreakerRecommendationsService circuitBreakerRecommendationsService;

    public CircuitBreakerRecommendationsController(
            CircuitBreakerRecommendationsService circuitBreakerRecommendationsService) {
        this.circuitBreakerRecommendationsService = circuitBreakerRecommendationsService;
    }

    @GetMapping
    public List<String> getRecommendationsNoCB() {
        return circuitBreakerRecommendationsService.getRecommendationsNoCB();
    }

    @GetMapping("/fallback")
    public List<String> getRecommendationsWithFallback() {
        return circuitBreakerRecommendationsService.getRecommendationsWithFallback();
    }

    @GetMapping("/states")
    public List<String> getRecommendations() {
        return circuitBreakerRecommendationsService.getRecommendations();
    }

    @GetMapping("/annotation")
    /*
    annotationCB will take the default props from application.yml;
    The Resilience4j Aspects order is following:
    Retry ( CircuitBreaker ( RateLimiter ( TimeLimiter ( Bulkhead ( Function ) ) ) ) )
    so Retry is applied at the end (if needed).
    */
    @CircuitBreaker(name = "annotationCB", fallbackMethod = "getDefaultRecommendations")
//    @RateLimiter(name = "propsRL")
    public List<String> getRecommendationsWithFallbackAnnotation() {
        return circuitBreakerRecommendationsService.getRecommendationsAnnotationCB();
    }

    @GetMapping("/replace")
    public ResponseEntity<Void> getNewCircuitBreakerConfig() {
        circuitBreakerRecommendationsService.replaceCB();
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @GetMapping("/feignBuilder")
    public List<String> getRecommendationsFeignBuilder() {
        return circuitBreakerRecommendationsService.getRecommendationsFeignBuilder();
    }

   /*
   Fallback methods -> should be placed in the same class
                    -> must have the same method signature with just ONE extra target exception parameter.

    If there are multiple fallbackMethod methods, the method that has the most closest match will be invoked
    */

    public List<String> getDefaultRecommendations(RetryableException e) {
        return List.of("Fallback Java Book 1", "Fallback Java Book 2");
    }

    public List<String> getDefaultRecommendations(CallUnsuccessful e) {
        return List.of("Fallback Java Book 3", "Fallback Java Book 4");
    }
}
