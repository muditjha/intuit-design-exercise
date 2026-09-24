package com.example.parking.store;

import com.example.parking.domain.Driver;
import java.util.Optional;

public interface DriverRepository {

    Optional<Driver> findById(String id);
}
