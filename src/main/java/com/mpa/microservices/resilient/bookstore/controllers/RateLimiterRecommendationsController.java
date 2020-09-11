package com.mpa.microservices.resilient.bookstore.controllers;

import com.mpa.microservices.resilient.bookstore.services.RecommendationsService;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ratelimiter")
public class RateLimiterRecommendationsController {

    private RecommendationsService recommendationsService;

    public RateLimiterRecommendationsController(RecommendationsService recommendationsService) {
        this.recommendationsService = recommendationsService;
    }

    @RequestMapping
    public ResponseEntity<Void> getRecommendationsWithRateLimiter() throws InterruptedException {
        recommendationsService.callOrderHistoryWithRateLimiter();
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @RequestMapping("/props")
    @RateLimiter(name ="propsRL")
    public List<String> getRecommendationsWithRateLimiterProps() throws InterruptedException {
       return recommendationsService.getOrderHistoryRL();
    }

    public List<String> getDefaultRecommendations(RequestNotPermitted e) {
        return List.of("Java Fallback Book 1", "Java Fallback Book 2");
    }
}
