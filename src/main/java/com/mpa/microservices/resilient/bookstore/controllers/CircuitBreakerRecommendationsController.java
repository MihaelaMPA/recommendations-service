package com.mpa.microservices.resilient.bookstore.controllers;

import com.mpa.microservices.resilient.bookstore.exceptions.CallUnsuccessful;
import com.mpa.microservices.resilient.bookstore.services.RecommendationsService;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.feign.FeignDecorators;
import io.github.resilience4j.ratelimiter.RateLimiter;
import java.util.List;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/recommendations")
public class CircuitBreakerRecommendationsController {

    private RecommendationsService recommendationsService;

    public CircuitBreakerRecommendationsController(RecommendationsService recommendationsService) {
        this.recommendationsService = recommendationsService;
    }

    @RequestMapping("/nocb")
    public List<String> getRecommendationsNoCB() {
        return recommendationsService.getRecommendationsNoCB();
    }

    @RequestMapping("/fb")
    public List<String> getRecommendationsWithFallback() {
        return recommendationsService.getRecommendationsWithFallback();
    }

    @RequestMapping("/afb")
    //annotationCB will take the default props from application.yml;
    //The Resilience4j Aspects order is following:
    //Retry ( CircuitBreaker ( RateLimiter ( TimeLimiter ( Bulkhead ( Function ) ) ) ) )
    //so Retry is applied at the end (if needed).
    @CircuitBreaker(name = "annotationCB", fallbackMethod = "getDefaultRecommendations")
//    @RateLimiter(name = "propsRL")
    public List<String> getRecommendationsWithFallbackAnnotation() {
        return recommendationsService.getRecommendationsAnnotationCB();
    }

    @RequestMapping
    public List<String> getRecommendations() {
        return recommendationsService.getRecommendations();
    }

    @RequestMapping("/props")
    //propsCB will take the props from deafult  + instance overrides application.yml ;
    @CircuitBreaker(name = "propsCB")
    public List<String> getRecommendationsCBProps() {
        return recommendationsService.getRecommendationsFromProps();
    }

   @RequestMapping("/feignBuilder")
   public List<String> getRecommendationsFeignBuilder(){
        return recommendationsService.getRecommendationsFeignBuilder();
   }

    public List<String> getDefaultRecommendations(RetryableException e) {
        return List.of("Java Fallback Book 1", "Java Fallback Book 2");
    }

    public List<String> getDefaultRecommendations(CallUnsuccessful e) {
        return List.of("Java Fallback Book 3", "Java Fallback Book 4");
    }
}