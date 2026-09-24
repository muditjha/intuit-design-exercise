package com.example.parking.store;

import com.example.parking.domain.Reservation;
import com.example.parking.domain.ReservationStatus;
import java.util.Optional;

public interface ReservationRepository {

    /** @throws IllegalStateException if a reservation with the same id already exists */
    void insert(Reservation reservation);

    Optional<Reservation> get(String id);

    /** Replaces the stored reservation only if its status and version still match. */
    boolean compareAndSet(String id, ReservationStatus expectedStatus, long expectedVersion, Reservation next);
}
