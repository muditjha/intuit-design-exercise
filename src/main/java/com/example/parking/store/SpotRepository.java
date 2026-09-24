package com.example.parking.store;

import com.example.parking.domain.Spot;
import java.util.Optional;

public interface SpotRepository {

    Optional<Spot> findById(String id);
}
