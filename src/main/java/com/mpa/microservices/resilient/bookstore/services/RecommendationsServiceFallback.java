package com.mpa.microservices.resilient.bookstore.services;

import com.mpa.microservices.resilient.bookstore.exceptions.CallUnsuccessful;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RecommendationsServiceFallback {

    public List<String> getDefaultRecommendations() throws CallUnsuccessful {
        return List.of("Fallback Java Book 1", "Fallback Java Book 2");
    }
}

