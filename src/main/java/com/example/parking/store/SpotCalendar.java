package com.example.parking.store;

import com.example.parking.domain.TimeWindow;

public interface SpotCalendar {

    /** Atomically books the window unless it overlaps an existing booking on the spot. */
    boolean tryBook(String spotId, TimeWindow window, String reservationId);

    /** Removes the reservation's booking if present; no-op otherwise. */
    void release(String spotId, String reservationId);
}
