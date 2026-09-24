package com.example.parking.store;

import com.example.parking.domain.Driver;
import java.util.Collection;
import java.util.Optional;

public class InMemoryDriverRepository implements DriverRepository {

    public InMemoryDriverRepository(Collection<Driver> seed) {
    }

    @Override
    public Optional<Driver> findById(String id) {
        throw new UnsupportedOperationException("not implemented");
    }
}
