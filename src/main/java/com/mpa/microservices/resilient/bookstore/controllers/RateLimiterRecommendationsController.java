package com.mpa.microservices.resilient.bookstore.controllers;

import com.mpa.microservices.resilient.bookstore.services.RecommendationsService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ratelimiter")
public class RateLimiterRecommendationsController {

    private RecommendationsService recommendationsService;

    public RateLimiterRecommendationsController(RecommendationsService recommendationsService) {
        this.recommendationsService = recommendationsService;
    }

    @GetMapping("/{id}")
    @RateLimiter(name = "propsRL")
    public List<String> getRecommendationsWithRateLimiterProps(@PathVariable String id) throws InterruptedException {
        return recommendationsService.getOrderHistoryRL(id);
    }

    @RequestMapping("/webclient")
    public List<String> getRecommendationsCBProps() {
        return recommendationsService.getRecommendationsWebClient();
    }
}
