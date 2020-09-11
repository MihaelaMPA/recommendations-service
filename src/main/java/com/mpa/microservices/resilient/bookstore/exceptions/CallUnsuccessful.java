package com.mpa.microservices.resilient.bookstore.exceptions;

public class CallUnsuccessful extends RuntimeException{

    public CallUnsuccessful(String message) {
        super(message);
    }
}
