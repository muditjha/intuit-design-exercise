package com.example.parking.store;

import com.example.parking.domain.IdempotencyRecord;
import com.example.parking.domain.IdempotencyStatus;
import java.util.Optional;

public class InMemoryIdempotencyStore implements IdempotencyStore {

    @Override
    public Optional<IdempotencyRecord> find(String key) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<IdempotencyRecord> putIfAbsent(IdempotencyRecord record) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public boolean complete(String key, IdempotencyStatus status, String resultRef) {
        throw new UnsupportedOperationException("not implemented");
    }
}
