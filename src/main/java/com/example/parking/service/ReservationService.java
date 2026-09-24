package com.example.parking.service;

import com.example.parking.domain.ReserveRequest;
import com.example.parking.domain.ReserveResult;
import com.example.parking.store.DriverRepository;
import com.example.parking.store.IdempotencyStore;
import com.example.parking.store.ReservationRepository;
import com.example.parking.store.SpotCalendar;
import com.example.parking.store.SpotRepository;
import java.time.Clock;

public class ReservationService {

    private final Clock clock;
    private final DriverRepository drivers;
    private final SpotRepository spots;
    private final IdempotencyStore idempotency;
    private final ReservationRepository reservations;
    private final SpotCalendar calendar;

    public ReservationService(
            Clock clock,
            DriverRepository drivers,
            SpotRepository spots,
            IdempotencyStore idempotency,
            ReservationRepository reservations,
            SpotCalendar calendar) {
        this.clock = clock;
        this.drivers = drivers;
        this.spots = spots;
        this.idempotency = idempotency;
        this.reservations = reservations;
        this.calendar = calendar;
    }

    /** @throws RequestInProgressException if the same request is still running under this key */
    public ReserveResult reserve(ReserveRequest request) {
        throw new UnsupportedOperationException("not implemented");
    }
}
