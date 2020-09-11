package com.mpa.microservices.resilient.bookstore.exceptions;

import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

@Component
public class FeignErrorDecoder implements ErrorDecoder {


    private final ErrorDecoder defaultErrorDecoder = new Default();

    @Override
    @SneakyThrows
    public Exception decode(String methodKey, Response response) {
        if (response.status() == 500) {
            throw new CallUnsuccessful("Unsuccessful call");
        }

        return defaultErrorDecoder.decode(methodKey, response);
    }
}
