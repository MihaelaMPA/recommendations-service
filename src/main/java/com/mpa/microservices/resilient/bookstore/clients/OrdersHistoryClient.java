package com.mpa.microservices.resilient.bookstore.clients;

import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "order-history-service", url = "http://localhost:9091/")
public interface OrdersHistoryClient {

    @GetMapping("/ordersHistoryCB/{id}")
    List<String> getOrdersForCB(@PathVariable String id);

    @GetMapping("/ordersHistoryRL")
    List<String> getOrdersForRL();
}
