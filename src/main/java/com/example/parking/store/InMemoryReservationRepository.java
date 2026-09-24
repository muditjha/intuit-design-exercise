package com.example.parking.store;

import com.example.parking.domain.Reservation;
import com.example.parking.domain.ReservationStatus;
import java.util.List;
import java.util.Optional;

public class InMemoryReservationRepository implements ReservationRepository {

    @Override
    public void insert(Reservation reservation) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<Reservation> get(String id) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public boolean compareAndSet(String id, ReservationStatus expectedStatus, long expectedVersion, Reservation next) {
        throw new UnsupportedOperationException("not implemented");
    }

    /** Read-only view for tests; not part of the interface. */
    public List<Reservation> all() {
        throw new UnsupportedOperationException("not implemented");
    }
}
