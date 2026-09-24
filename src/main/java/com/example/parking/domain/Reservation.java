package com.example.parking.domain;

/** Inserted at version 0; every compare-and-set transition increments the version by 1. */
public record Reservation(
        String id,
        String driverId,
        String spotId,
        TimeWindow window,
        ReservationStatus status,
        Reason reason,
        long version) {
}
