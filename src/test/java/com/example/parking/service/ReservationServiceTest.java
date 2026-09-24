package com.example.parking.service;

import static com.example.parking.domain.ReserveResult.Status.FAILED;
import static com.example.parking.domain.ReserveResult.Status.REJECTED;
import static com.example.parking.domain.ReserveResult.Status.SUCCEEDED;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.parking.domain.Driver;
import com.example.parking.domain.IdempotencyStatus;
import com.example.parking.domain.Reason;
import com.example.parking.domain.Reservation;
import com.example.parking.domain.ReservationStatus;
import com.example.parking.domain.ReserveRequest;
import com.example.parking.domain.ReserveResult;
import com.example.parking.domain.Spot;
import com.example.parking.domain.TimeWindow;
import com.example.parking.store.InMemoryDriverRepository;
import com.example.parking.store.InMemoryIdempotencyStore;
import com.example.parking.store.InMemoryReservationRepository;
import com.example.parking.store.InMemorySpotCalendar;
import com.example.parking.store.InMemorySpotRepository;
import com.example.parking.store.SpotCalendar;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ReservationServiceTest {

    private static final Instant MIDNIGHT = Instant.parse("2026-09-24T00:00:00Z");
    private static final Instant NOW = at(9, 0);
    private static final String GARAGE = "garage-1";
    private static final String SPOT_A = "spot-A";
    private static final String SPOT_B = "spot-B";

    private MutableClock clock;
    private InMemoryDriverRepository drivers;
    private InMemorySpotRepository spots;
    private InMemoryIdempotencyStore idempotency;
    private InMemoryReservationRepository reservations;
    private InMemorySpotCalendar calendar;
    private ReservationService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        drivers = new InMemoryDriverRepository(
                IntStream.range(0, 32).mapToObj(i -> new Driver("driver-" + i, "Driver " + i)).toList());
        spots = new InMemorySpotRepository(List.of(
                new Spot(SPOT_A, GARAGE, "L1-01"),
                new Spot(SPOT_B, GARAGE, "L1-02")));
        idempotency = new InMemoryIdempotencyStore();
        reservations = new InMemoryReservationRepository();
        calendar = new InMemorySpotCalendar();
        service = newService(calendar);
    }

    // --- Acceptance criteria ---

    @Test
    void bookingAFutureWindowCreatesAnActiveReservation() {
        TimeWindow window = window(10, 0, 11, 0);

        ReserveResult result = service.reserve(request("key-1", "driver-0", SPOT_A, window));

        assertEquals(SUCCEEDED, result.status());
        assertNotNull(result.reservationId());
        assertEquals(null, result.reason());
        assertEquals(
                Optional.of(new Reservation(result.reservationId(), "driver-0", SPOT_A, window,
                        ReservationStatus.ACTIVE, null, 1)),
                reservations.get(result.reservationId()));
        assertEquals(List.of(result.reservationId()), calendar.bookings(SPOT_A));
        assertEquals(IdempotencyStatus.SUCCEEDED, idempotency.find("key-1").orElseThrow().status());
        assertEquals(result.reservationId(), idempotency.find("key-1").orElseThrow().resultRef());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    void invalidRequestIsRejectedAndWritesNothing(String caseName, ReserveRequest request, Reason expected) {
        ReserveResult result = service.reserve(request);

        assertEquals(new ReserveResult(REJECTED, null, expected), result);
        assertEquals(List.of(), reservations.all());
        assertEquals(List.of(), calendar.bookings(request.spotId()));
        assertEquals(Optional.empty(), idempotency.find(request.idempotencyKey()));
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Arguments.of("unknown driver",
                        request("key-1", "ghost", SPOT_A, window(10, 0, 11, 0)), Reason.UNKNOWN_DRIVER),
                Arguments.of("unknown spot",
                        request("key-1", "driver-0", "spot-Z", window(10, 0, 11, 0)), Reason.UNKNOWN_SPOT),
                Arguments.of("start equals now",
                        request("key-1", "driver-0", SPOT_A, window(9, 0, 10, 0)), Reason.START_NOT_IN_FUTURE),
                Arguments.of("start before now",
                        request("key-1", "driver-0", SPOT_A, window(8, 0, 10, 0)), Reason.START_NOT_IN_FUTURE),
                Arguments.of("end equals start",
                        request("key-1", "driver-0", SPOT_A, window(10, 0, 10, 0)), Reason.END_NOT_AFTER_START),
                Arguments.of("end before start",
                        request("key-1", "driver-0", SPOT_A, window(11, 0, 10, 0)), Reason.END_NOT_AFTER_START));
    }

    @Test
    void overlappingWindowOnSameSpotIsRejectedSlotTaken() {
        ReserveResult first = service.reserve(request("key-1", "driver-0", SPOT_A, window(10, 0, 12, 0)));
        TimeWindow overlapping = window(11, 0, 13, 0);

        ReserveResult second = service.reserve(request("key-2", "driver-1", SPOT_A, overlapping));

        assertEquals(REJECTED, second.status());
        assertEquals(Reason.SLOT_TAKEN, second.reason());
        assertNotNull(second.reservationId());
        // E5: a conflict found at write time leaves a REJECTED record as history.
        assertEquals(
                Optional.of(new Reservation(second.reservationId(), "driver-1", SPOT_A, overlapping,
                        ReservationStatus.REJECTED, Reason.SLOT_TAKEN, 1)),
                reservations.get(second.reservationId()));
        assertEquals(IdempotencyStatus.REJECTED, idempotency.find("key-2").orElseThrow().status());
        assertEquals(List.of(first.reservationId()), calendar.bookings(SPOT_A));
    }

    @Test
    void adjacentWindowsOnSameSpotBothSucceed() {
        ReserveResult first = service.reserve(request("key-1", "driver-0", SPOT_A, window(10, 0, 11, 0)));
        ReserveResult second = service.reserve(request("key-2", "driver-1", SPOT_A, window(11, 0, 12, 0)));

        assertEquals(SUCCEEDED, first.status());
        assertEquals(SUCCEEDED, second.status());
        assertEquals(2, calendar.bookings(SPOT_A).size());
        assertTrue(calendar.bookings(SPOT_A).containsAll(List.of(first.reservationId(), second.reservationId())));
    }

    @Test
    void replayingSameKeyAndRequestReturnsSameOutcomeWithoutSecondBooking() {
        ReserveRequest request = request("key-1", "driver-0", SPOT_A, window(10, 0, 11, 0));
        ReserveResult first = service.reserve(request);

        ReserveResult replay = service.reserve(request);

        assertEquals(first, replay);
        assertEquals(1, reservations.all().size());
        assertEquals(List.of(first.reservationId()), calendar.bookings(SPOT_A));
    }

    @Test
    void replayAfterStartHasPassedStillReturnsStoredOutcome() {
        ReserveRequest request = request("key-1", "driver-0", SPOT_A, window(10, 0, 11, 0));
        ReserveResult first = service.reserve(request);
        clock.set(at(10, 30));

        ReserveResult replay = service.reserve(request);

        assertEquals(first, replay);
        assertEquals(1, reservations.all().size());
    }

    @Test
    void reusingKeyWithDifferentRequestIsRejectedKeyReusedAndWritesNothing() {
        service.reserve(request("key-1", "driver-0", SPOT_A, window(10, 0, 11, 0)));

        ReserveResult result = service.reserve(request("key-1", "driver-0", SPOT_B, window(10, 0, 11, 0)));

        assertEquals(new ReserveResult(REJECTED, null, Reason.KEY_REUSED), result);
        assertEquals(1, reservations.all().size());
        assertEquals(List.of(), calendar.bookings(SPOT_B));
    }

    @Test
    void sameKeyWhileFirstRequestIsInProgressThrows() throws Exception {
        BlockingSpotCalendar blocking = new BlockingSpotCalendar(calendar);
        ReservationService blockingService = newService(blocking);
        ReserveRequest request = request("key-1", "driver-0", SPOT_A, window(10, 0, 11, 0));
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<ReserveResult> first =
                    CompletableFuture.supplyAsync(() -> blockingService.reserve(request), pool);
            // Completes when the first call is parked inside tryBook, or rethrows its failure if it never got there.
            CompletableFuture.anyOf(blocking.entered, first).get(5, SECONDS);

            assertThrows(RequestInProgressException.class, () -> blockingService.reserve(request));

            blocking.proceed.countDown();
            ReserveResult firstResult = first.get(5, SECONDS);
            assertEquals(SUCCEEDED, firstResult.status());
            assertEquals(List.of(firstResult.reservationId()), calendar.bookings(SPOT_A));
        } finally {
            blocking.proceed.countDown();
            pool.shutdownNow();
        }
    }

    // --- Correctness tests ---

    @RepeatedTest(50)
    void concurrentOverlappingBookingsOnOneSpotYieldExactlyOneActive() throws Exception {
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ReserveResult>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                // [10:00+i, 11:00+i): every window contains 10:59, so every pair overlaps.
                ReserveRequest request = request("key-" + i, "driver-" + i, SPOT_A, window(10, i, 11, i));
                futures.add(pool.submit(() -> {
                    start.await();
                    return service.reserve(request);
                }));
            }
            start.countDown();

            List<ReserveResult> results = new ArrayList<>();
            for (Future<ReserveResult> future : futures) {
                results.add(future.get(10, SECONDS));
            }

            List<ReserveResult> winners = results.stream().filter(r -> r.status() == SUCCEEDED).toList();
            assertEquals(1, winners.size());
            assertEquals(threads - 1, results.stream()
                    .filter(r -> r.status() == REJECTED && r.reason() == Reason.SLOT_TAKEN)
                    .count());
            assertEquals(1, reservations.all().stream()
                    .filter(r -> r.status() == ReservationStatus.ACTIVE)
                    .count());
            assertEquals(List.of(winners.get(0).reservationId()), calendar.bookings(SPOT_A));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void calendarFailureAfterBookingUnwindsToFailedAndLeavesNoBooking() {
        ReservationService failingService = newService(new FailingOnceSpotCalendar(calendar));
        TimeWindow window = window(10, 0, 11, 0);

        ReserveResult result = failingService.reserve(request("key-1", "driver-0", SPOT_A, window));

        assertEquals(FAILED, result.status());
        assertEquals(Reason.UNEXPECTED_ERROR, result.reason());
        assertNotNull(result.reservationId());
        assertEquals(ReservationStatus.FAILED, reservations.get(result.reservationId()).orElseThrow().status());
        assertEquals(IdempotencyStatus.FAILED, idempotency.find("key-1").orElseThrow().status());
        assertEquals(List.of(), calendar.bookings(SPOT_A));
        // The slot is genuinely free again: a new request for the same window succeeds.
        assertEquals(SUCCEEDED, failingService.reserve(request("key-2", "driver-1", SPOT_A, window)).status());
    }

    // --- Helpers ---

    private ReservationService newService(SpotCalendar spotCalendar) {
        return new ReservationService(clock, drivers, spots, idempotency, reservations, spotCalendar);
    }

    private static Instant at(int hour, int minute) {
        return MIDNIGHT.plus(Duration.ofHours(hour).plusMinutes(minute));
    }

    private static TimeWindow window(int startHour, int startMinute, int endHour, int endMinute) {
        return new TimeWindow(at(startHour, startMinute), at(endHour, endMinute));
    }

    private static ReserveRequest request(String key, String driverId, String spotId, TimeWindow window) {
        return new ReserveRequest(key, driverId, spotId, window);
    }

    /** Clock the test can move forward. */
    private static final class MutableClock extends Clock {
        private volatile Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant now) {
            this.now = now;
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    /** Books through the real calendar, then throws once, simulating a crash after the write. */
    private static final class FailingOnceSpotCalendar implements SpotCalendar {
        private final SpotCalendar delegate;
        private final AtomicBoolean failed = new AtomicBoolean();

        FailingOnceSpotCalendar(SpotCalendar delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean tryBook(String spotId, TimeWindow window, String reservationId) {
            boolean booked = delegate.tryBook(spotId, window, reservationId);
            if (failed.compareAndSet(false, true)) {
                throw new IllegalStateException("injected failure after booking");
            }
            return booked;
        }

        @Override
        public void release(String spotId, String reservationId) {
            delegate.release(spotId, reservationId);
        }
    }

    /** Parks tryBook until the test says proceed, so a request can be observed IN_PROGRESS. */
    private static final class BlockingSpotCalendar implements SpotCalendar {
        final CompletableFuture<Void> entered = new CompletableFuture<>();
        final CountDownLatch proceed = new CountDownLatch(1);
        private final SpotCalendar delegate;

        BlockingSpotCalendar(SpotCalendar delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean tryBook(String spotId, TimeWindow window, String reservationId) {
            entered.complete(null);
            try {
                if (!proceed.await(5, SECONDS)) {
                    throw new IllegalStateException("test never released the blocked booking");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return delegate.tryBook(spotId, window, reservationId);
        }

        @Override
        public void release(String spotId, String reservationId) {
            delegate.release(spotId, reservationId);
        }
    }
}
