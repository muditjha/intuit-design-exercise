package com.example.parking.store;

import com.example.parking.domain.TimeWindow;
import java.util.List;

/** Per-spot lock; stands in for a DB exclusion constraint. */
public class InMemorySpotCalendar implements SpotCalendar {

    @Override
    public boolean tryBook(String spotId, TimeWindow window, String reservationId) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public void release(String spotId, String reservationId) {
        throw new UnsupportedOperationException("not implemented");
    }

    /** Read-only view for tests: reservation ids currently booked on the spot. Not part of the interface. */
    public List<String> bookings(String spotId) {
        throw new UnsupportedOperationException("not implemented");
    }
}
