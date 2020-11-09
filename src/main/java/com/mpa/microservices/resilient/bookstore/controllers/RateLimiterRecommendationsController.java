package com.mpa.microservices.resilient.bookstore.controllers;

import com.mpa.microservices.resilient.bookstore.services.RateLimiterRecommendationsService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ratelimiter")
public class RateLimiterRecommendationsController {

    private RateLimiterRecommendationsService rateLimiterRecommendationsService;

    public RateLimiterRecommendationsController(
            RateLimiterRecommendationsService rateLimiterRecommendationsService) {
        this.rateLimiterRecommendationsService = rateLimiterRecommendationsService;
    }

    @GetMapping("/{id}")
    public List<String> getRecommendationsWithRateLimiterProps(@PathVariable String id) {
        return rateLimiterRecommendationsService.getOrderHistoryRL(id);
    }

    @GetMapping("/webclient")
    public List<String> getRecommendationsCBProps() {
        return rateLimiterRecommendationsService.getRecommendationsWebClient();
    }
}
