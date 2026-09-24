package com.example.parking.store;

import com.example.parking.domain.IdempotencyRecord;
import com.example.parking.domain.IdempotencyStatus;
import java.util.Optional;

public interface IdempotencyStore {

    Optional<IdempotencyRecord> find(String key);

    /** Atomically inserts the record. Returns the existing record if the key is taken, empty if inserted. */
    Optional<IdempotencyRecord> putIfAbsent(IdempotencyRecord record);

    /** Moves the record from IN_PROGRESS to a final status. Returns false if it was not IN_PROGRESS. */
    boolean complete(String key, IdempotencyStatus status, String resultRef);
}
