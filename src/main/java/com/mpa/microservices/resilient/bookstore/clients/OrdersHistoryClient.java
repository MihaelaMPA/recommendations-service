package com.mpa.microservices.resilient.bookstore.clients;

import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "order-history-service", url = "http://localhost:9091/")
public interface OrdersHistoryClient {

    @GetMapping("/ordersHistoryCB")
    List<String> getOrdersForCB();

    @GetMapping("/ordersHistoryRL")
    List<String> getOrdersForRL();

    @GetMapping("/exception")
    List<String> getOrdersException();
}
