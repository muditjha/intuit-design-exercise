package com.example.parking.domain;

public enum Reason {
    UNKNOWN_DRIVER,
    UNKNOWN_SPOT,
    START_NOT_IN_FUTURE,
    END_NOT_AFTER_START,
    SLOT_TAKEN,
    KEY_REUSED,
    UNEXPECTED_ERROR
}
