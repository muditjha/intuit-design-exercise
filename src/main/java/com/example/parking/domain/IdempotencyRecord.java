package com.example.parking.domain;

/** resultRef is the reservation id the request produced, or null if it produced none. */
public record IdempotencyRecord(String key, String requestFingerprint, IdempotencyStatus status, String resultRef) {
}
