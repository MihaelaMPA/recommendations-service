package com.mpa.microservices.resilient.bookstore.clients;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.feign.FeignDecorators;
import io.github.resilience4j.ratelimiter.RateLimiter;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@FeignClient(name = "order-history-service", url = "http://localhost:9091/")
public interface OrdersHistoryClient {

    //    @RequestMapping(value = "/ordersHistoryCB/{id}")
//    @RequestMapping(method = RequestMethod.GET, value = "/ordersHistoryCB/{id}")
    @GetMapping("/ordersHistoryCB/{id}")
    List<String> getOrdersForCB(@PathVariable String id);

    //    @RequestMapping(value = "/ordersHistoryRL")
    @GetMapping("/ordersHistoryRL")
    List<String> getOrdersForRL();
}
