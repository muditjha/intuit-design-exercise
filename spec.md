# Parking Garage Hub — design spec

## Problem
Drivers book a specific spot in a garage for a future time window, check in when they arrive, and check out when they leave. Operators see, for any instant, which spots are FREE, HELD, OCCUPIED, or OFFLINE, and schedule maintenance windows without losing history. Many drivers and operators act at once; a spot is in exactly one meaningful state at any instant.

## Scale
Unknown. v1 is a single JVM, in-memory, with every store behind an interface so a real database can replace it.

## Scope
- In: book a spot for a future window, one live reservation per driver, check-in / check-out, no-show expiry, availability snapshot, change spot, scheduled maintenance.
- Out: payments and pricing, authentication, HTTP, persistence, auto-assigning a spot, notifications, garage/spot/driver/operator admin CRUD.

## Assumptions
- A1 Garages, spots, drivers and operators are pre-seeded reference data; ids are globally unique; no create/edit in v1.
- A2 Windows are half-open `[start, end)`; `start > now`; no max lead time or max duration in v1.
- A3 Check-in is allowed only in `[start, start + grace)`; grace = 15 min, configurable. Early arrival is rejected.
- A4 No-show = not checked in by `start + grace` → EXPIRED.
- A5 Checkout is allowed any time after check-in and frees the rest of the window.
- A6 "One spot at a time" = at most one ACTIVE or CHECKED_IN reservation per driver (parked counts).
- A7 Change spot keeps the same window and is allowed only before check-in.
- A8 Plain driver cancel is not in v1.
- A9 Idempotency keys on `reserve`, `changeSpot`, `scheduleMaintenance`. `checkIn` / `checkOut` are idempotent by state.
- A10 Maintenance may start now or later; maintenance windows can't be edited in v1.
- A11 `driverId` and `operatorId` are trusted input (no auth); they're checked only for existence and garage scope.
- A12 An operator may schedule maintenance only on spots in their `garageIds` and may read availability for those garages.
- A13 The one-spot rule is per driver, not per vehicle; there is no Vehicle entity.
- No money and no external calls in v1, so the money and external-call rules don't apply yet.

## Components

### Entities
| Entity | Fields | Why it exists / what it validates |
|---|---|---|
| `Garage` | `id, name` | Groups spots; availability is reported per garage; unknown garage is rejected. |
| `Spot` | `id, garageId, label` | The thing that gets booked. Never deleted. `label` is the human spot number, e.g. "L2-14". |
| `Driver` | `id, name` | Books, checks in and out. Unknown driver is rejected. The one-spot rule (I2) is keyed on `Driver.id`. |
| `Operator` | `id, name, garageIds` | Schedules maintenance and reads availability, scoped to `garageIds` (A12). |
| `Reservation` | `id, driverId, spotId, window, status, reason, replacesId, version` | A driver's booking of one spot for one window. Also the saga record and the history. |
| `MaintenanceWindow` | `id, operatorId, spotId, window, status, version` | An operator's scheduled block on a spot; records who did it. |
| `IdempotencyRecord` | `key, requestFingerprint, status, resultRef` | Makes client retries safe. |

Value object: `TimeWindow(start, end)` — `Instant`s, half-open; adjacent windows don't overlap.

Relationships: Garage 1—N Spot · Driver 1—N Reservation (≤1 live, I2) · Spot 1—N Reservation / MaintenanceWindow · Operator N—N Garage.

### Stores (interface + in-memory impl); the atomic operations live here
- `SpotCalendar`
  - `tryBook(spotId, window, reservationId)` — atomically rejects any overlap with bookings or maintenance blocks.
  - `addBlock(spotId, window)` — atomically inserts a block and returns the ids of overlapping bookings.
  - `release(spotId, reservationId)`
  - In-memory: per-spot lock. Stands in for a DB exclusion constraint.
- `DriverIndex` — `claim(driverId, resId)` putIfAbsent · `swap(driverId, oldId, newId)` CAS · `release(driverId, resId)` conditional.
- `OccupancyIndex` — `claim(spotId, resId)` putIfAbsent (one car per spot) · `release(spotId, resId)` conditional.
- `ReservationRepository` — `insert`, `get`, `compareAndSet(id, expectedStatus, expectedVersion, next)`.
- `MaintenanceRepository` — `insert`, `get`, `compareAndSet`.
- `IdempotencyStore` — `find` (read-only), `putIfAbsent`, `complete`.
- Read-only reference repos: `GarageRepository`, `SpotRepository` (`findById`, `findByGarage`), `DriverRepository`, `OperatorRepository`.

### Services
- `ReservationService` — `reserve`, `changeSpot`, `checkIn`, `checkOut`.
- `MaintenanceService` — `scheduleMaintenance`.
- `ExpirySweeper` — expires no-shows; unwinds stale PENDING records.
- `AvailabilityService` — `snapshot(garageId, t)`.
- `java.time.Clock` is injected everywhere.

### Reserve flow (the reference shape for all write flows)
`reserve` returns `ReserveResult(status, reservationId, reason)`, with status SUCCEEDED, REJECTED or FAILED.

0. Idempotency lookup, read-only: `IdempotencyStore.find(key)`. If a record exists, go to **Key exists** below. (This comes first so a retry after `start` has passed still returns its stored outcome.)
1. Validate, read-only: driver exists, spot exists, `now < start < end`. Rejected → REJECTED with a reason, no reservationId, nothing written.
2. `IdempotencyStore.putIfAbsent` (IN_PROGRESS) — the first write. If a concurrent request inserted the key first, go to **Key exists**.
3. Insert Reservation `PENDING` (the saga record; a crash leaves something the sweeper can find).
4. `DriverIndex.claim` → fails: reservation REJECTED(`DRIVER_HAS_RESERVATION`).
5. `SpotCalendar.tryBook` → fails: release the driver claim, reservation REJECTED(`SLOT_TAKEN`).
6. CAS `PENDING → ACTIVE`; complete the idempotency record.

Any exception from step 3 onward → undo completed steps in reverse, reservation FAILED (if it was inserted), idempotency record FAILED. The sweeper unwinds stale PENDING records the same way.

**Key exists:** different fingerprint → REJECTED(`KEY_REUSED`), nothing written. Same fingerprint and IN_PROGRESS → throw `RequestInProgressException`. Same fingerprint and completed → return the stored outcome unchanged.

## States

### Reservation
```
PENDING ──> ACTIVE ──> CHECKED_IN ──> COMPLETED
   │           ├──> EXPIRED
   │           └──> CANCELLED (reason: CHANGED | MAINTENANCE)
   └──> REJECTED | FAILED
```
All transitions are CAS on `(id, expectedStatus, version)`. Terminal: COMPLETED, EXPIRED, CANCELLED, REJECTED, FAILED.

### Spot at instant t — derived, never stored
First match wins, so exactly one applies:
1. OCCUPIED — a CHECKED_IN reservation holds the spot's occupancy slot.
2. OFFLINE — a maintenance window covers t. (Drain falls out of this: while the car is there → OCCUPIED; after checkout → OFFLINE until the window ends.)
3. HELD — an ACTIVE reservation with `start ≤ t < start + grace`.
4. FREE — otherwise.

## Invariants
- I1 No two non-terminal reservations or blocks overlap on one spot's calendar, except a maintenance block whose overlapping reservations are still being cancelled.
- I2 A driver has at most one reservation in ACTIVE or CHECKED_IN (enforced by `DriverIndex`).
- I3 A spot has at most one CHECKED_IN reservation (enforced by `OccupancyIndex`).
- I4 Nothing is deleted. History = reservation and maintenance records with terminal status and reason.
- I5 Availability is derived only from source records. No counters that can drift.
- I6 Every non-terminal record reaches a terminal state via its flow, its unwind, or the sweeper.

## Known edge cases
- E1 Change spot briefly gives a driver two calendar bookings. I2 is enforced by `DriverIndex` (atomic swap), not the calendar.
- E2 **OPEN — needs sign-off.** Maintenance can't be unwound once it starts cancelling reservations; un-cancelling would be wrong. Proposed: roll forward. The maintenance record stays SCHEDULING until every overlap is processed; a retry with the same key, or the sweeper, finishes it. This departs from the "unwind" rule.
- E3 Overstay: a parked car runs past `end` while the next booking starts. v1: the next driver's check-in fails `SPOT_OCCUPIED`; they can change spot.
- E4 Drain can overrun: if the car stays past the maintenance end, maintenance effectively never happens. v1 accepts this.
- E5 Conflicts found at write time (slot taken, driver busy) leave REJECTED records after the idempotency record. "A rejected request writes nothing" applies to validation failures.
- E8 A crash between `putIfAbsent` and `complete` leaves the key IN_PROGRESS, so every retry throws `RequestInProgressException` until the sweeper (Story 4) resolves the record.
- E6 A no-show blocks new bookings until the next sweep; the view shows FREE after grace. This errs on the safe side.
- E7 The snapshot isn't atomic across spots: each spot is correct at the moment it's read, not all at one frozen instant.

## Phasing
- v1: stories 1–7 in `stories.md`.
- v2: auto-assign a spot, driver cancel, overstay policy, early check-in, change time as well as spot, atomic cross-spot snapshot, payments, notifications.
