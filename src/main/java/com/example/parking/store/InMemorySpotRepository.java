package com.example.parking.store;

import com.example.parking.domain.Spot;
import java.util.Collection;
import java.util.Optional;

public class InMemorySpotRepository implements SpotRepository {

    public InMemorySpotRepository(Collection<Spot> seed) {
    }

    @Override
    public Optional<Spot> findById(String id) {
        throw new UnsupportedOperationException("not implemented");
    }
}
