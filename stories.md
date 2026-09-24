# Stories — build in this order

## Story 1 — Book a spot for a future window
**As a** driver, **I can** book a specific spot for a future time window, so that the spot is mine for that window.

Acceptance criteria
- Unknown driver, unknown spot, `start ≤ now`, or `end ≤ start` is rejected and writes nothing.
- A window overlapping an existing live booking on the same spot is rejected `SLOT_TAKEN`.
- Adjacent windows (`[a,b)` then `[b,c)`) on the same spot both succeed.
- Replaying the same idempotency key with the same request returns the same outcome and creates no second booking.
- Reusing a key with a different request is rejected `KEY_REUSED`.

Correctness test
- `@RepeatedTest(50)`: N threads book overlapping windows on one spot → exactly one ACTIVE reservation and one calendar booking.
- Inject a failing `SpotCalendar` → reservation FAILED, idempotency record FAILED, no booking left behind.

## Story 2 — One reservation per driver
**As a** driver, **I can** hold only one spot at a time, so that no one hoards spots.

Acceptance criteria
- A second booking while the driver has an ACTIVE or CHECKED_IN reservation is rejected `DRIVER_HAS_RESERVATION`.
- Once the earlier reservation reaches a terminal state, the driver can book again.

Correctness test
- `@RepeatedTest(50)`: the same driver races bookings on different spots → exactly one ACTIVE; every loser's calendar booking is released.

## Story 3 — Check in and check out
**As a** driver, **I can** check in when I arrive and check out when I leave, so that the spot shows as occupied only while I'm there.

Acceptance criteria
- Only the reservation's owner can check in, and only in `[start, start + grace)`.
- Checkout moves the reservation to COMPLETED and releases the calendar, driver, and occupancy slots.
- Repeating a check-in or check-out that already happened returns the current state without a second transition.
- If a previous car is still parked (overstay), check-in is rejected `SPOT_OCCUPIED`.

Correctness test
- `@RepeatedTest(50)`: concurrent double check-in on one reservation → exactly one transition to CHECKED_IN.

## Story 4 — No-show expiry
**As an** operator, **I can** rely on unclaimed reservations expiring, so that spots aren't held by drivers who never arrive.

Acceptance criteria
- The sweeper moves ACTIVE reservations with `now ≥ start + grace` to EXPIRED and releases their calendar and driver slots.
- A late check-in is rejected even if the sweeper hasn't run yet.
- The sweeper unwinds stale PENDING reservations to FAILED and releases whatever they claimed.

Correctness test
- `@RepeatedTest(50)`: sweeper vs check-in racing at the grace boundary → exactly one of EXPIRED or CHECKED_IN.

## Story 5 — Availability snapshot
**As an** operator, **I can** see each spot's state in my garage at an instant, so that I know what's free, held, occupied, or offline.

Acceptance criteria
- `snapshot(garageId, t)` returns each spot's state (priority OCCUPIED > OFFLINE > HELD > FREE) and per-state counts.
- Rejected for an unknown garage or for a garage outside the operator's `garageIds`.
- Derived only from source records; no stored counters.

Correctness test
- Under concurrent bookings, check-ins and checkouts, every spot appears exactly once, so the counts sum to the garage's spot total.

## Story 6 — Change spot
**As a** driver, **I can** move my reservation to a different spot, so that I can switch without risking losing the spot I have.

Acceptance criteria
- Same window; allowed only while the old reservation is ACTIVE.
- The new spot is booked first; the old reservation moves to CANCELLED(CHANGED) only after that; then the driver index is swapped.
- If the new booking fails, the old reservation is unchanged.

Correctness test
- Inject a failure when cancelling the old reservation → new booking released and marked FAILED; old still ACTIVE.
- `@RepeatedTest(50)`: change spot vs check-in on the old reservation → the driver ends with exactly one live reservation.

## Story 7 — Scheduled maintenance
**As an** operator, **I can** schedule a maintenance window on a spot, so that it can be serviced without losing its history.

Acceptance criteria
- The operator must manage the spot's garage.
- Overlapping ACTIVE or PENDING reservations move to CANCELLED(MAINTENANCE).
- A CHECKED_IN reservation is kept; the spot shows OFFLINE after checkout until the window ends (drain).
- New bookings overlapping the window are rejected.
- Partial-failure handling follows the resolution of spec E2 (open).

Correctness test
- `@RepeatedTest(50)`: maintenance vs concurrent bookings on the same spot → afterwards no ACTIVE reservation overlaps the block.
