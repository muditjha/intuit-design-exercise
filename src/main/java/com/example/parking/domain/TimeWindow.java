package com.example.parking.domain;

import java.time.Instant;

/** Half-open window [start, end). Adjacent windows do not overlap. */
public record TimeWindow(Instant start, Instant end) {

    public boolean overlaps(TimeWindow other) {
        throw new UnsupportedOperationException("not implemented");
    }
}
