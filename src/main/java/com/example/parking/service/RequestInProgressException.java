package com.example.parking.service;

/** A request with the same idempotency key is still running. Retryable. */
public class RequestInProgressException extends RuntimeException {

    public RequestInProgressException(String idempotencyKey) {
        super("request in progress for idempotency key " + idempotencyKey);
    }
}
