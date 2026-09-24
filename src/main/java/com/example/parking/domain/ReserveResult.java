package com.example.parking.domain;

/** reservationId is null when nothing was written; reason is null on success. */
public record ReserveResult(Status status, String reservationId, Reason reason) {

    public enum Status {
        SUCCEEDED,
        REJECTED,
        FAILED
    }
}
