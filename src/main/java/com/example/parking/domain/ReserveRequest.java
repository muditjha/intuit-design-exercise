package com.example.parking.domain;

public record ReserveRequest(String idempotencyKey, String driverId, String spotId, TimeWindow window) {
}
