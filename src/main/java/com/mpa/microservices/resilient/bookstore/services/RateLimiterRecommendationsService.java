package com.mpa.microservices.resilient.bookstore.services;

import com.mpa.microservices.resilient.bookstore.clients.OrdersHistoryClient;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class RateLimiterRecommendationsService {

    private int counter = 0;

    private RecommendationsServiceFallback recommendationsServiceFallback;
    private OrdersHistoryClient ordersHistoryClient;
    private RateLimiterRegistry rateLimiterRegistry;

    public RateLimiterRecommendationsService(RecommendationsServiceFallback recommendationsServiceFallback,
            OrdersHistoryClient ordersHistoryClient, RateLimiterRegistry rateLimiterRegistry) {
        this.recommendationsServiceFallback = recommendationsServiceFallback;
        this.ordersHistoryClient = ordersHistoryClient;
        this.rateLimiterRegistry = rateLimiterRegistry;
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

        printRateLimiterConfigs(rateLimiter);
        return listMono.block(Duration.ofSeconds(1));
    }

    @io.github.resilience4j.ratelimiter.annotation.RateLimiter(name = "propsRL")
    public List<String> getOrderHistoryRL(String id) {
        if ("1".equals(id)) {
            RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter("propsRL");
            rateLimiter.changeLimitForPeriod(3);
        }

        return ordersHistoryClient.getOrdersForRL();
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

