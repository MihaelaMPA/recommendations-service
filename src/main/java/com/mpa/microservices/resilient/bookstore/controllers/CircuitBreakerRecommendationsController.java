package com.mpa.microservices.resilient.bookstore.controllers;

import com.mpa.microservices.resilient.bookstore.services.CircuitBreakerRecommendationsService;
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

    @GetMapping("/withoutCB")
    public List<String> getRecommendationsNoCB() {
        return circuitBreakerRecommendationsService.getRecommendationsNoCB();
    }

    @GetMapping("/withCB")
    public List<String> getRecommendationsWithFallback() {
        return circuitBreakerRecommendationsService.getRecommendationsWithFallback();
    }

    @GetMapping("/states")
    public List<String> getRecommendations() {
        return circuitBreakerRecommendationsService.getRecommendations();
    }

    @GetMapping("/annotation")
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
}
