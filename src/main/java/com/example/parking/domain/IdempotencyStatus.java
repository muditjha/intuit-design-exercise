package com.example.parking.domain;

public enum IdempotencyStatus {
    IN_PROGRESS,
    SUCCEEDED,
    REJECTED,
    FAILED
}
