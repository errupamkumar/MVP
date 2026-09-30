package com.srmecotech.plantride.matching;

import com.srmecotech.plantride.booking.Booking;
import com.srmecotech.plantride.booking.BookingRepository;
import com.srmecotech.plantride.booking.BookingStatus;
import com.srmecotech.plantride.booking.RideType;
import com.srmecotech.plantride.common.settings.SettingsService;
import com.srmecotech.plantride.masterdata.ComplianceService;
import com.srmecotech.plantride.masterdata.Driver;
import com.srmecotech.plantride.masterdata.RoutePlan;
import com.srmecotech.plantride.masterdata.Vehicle;
import com.srmecotech.plantride.masterdata.VehicleStatus;
import com.srmecotech.plantride.masterdata.VehicleType;
import com.srmecotech.plantride.trip.Duty;
import com.srmecotech.plantride.trip.DutyRepository;
import com.srmecotech.plantride.trip.Trip;
import com.srmecotech.plantride.trip.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;

import static com.srmecotech.plantride.support.Fixtures.ADMIN;
import static com.srmecotech.plantride.support.Fixtures.CLOCK;
import static com.srmecotech.plantride.support.Fixtures.GATE2;
import static com.srmecotech.plantride.support.Fixtures.TODAY;
import static com.srmecotech.plantride.support.Fixtures.TOWNSHIP;
import static com.srmecotech.plantride.support.Fixtures.cab;
import static com.srmecotech.plantride.support.Fixtures.driver;
import static com.srmecotech.plantride.support.Fixtures.duty;
import static com.srmecotech.plantride.support.Fixtures.otherRoute;
import static com.srmecotech.plantride.support.Fixtures.properties;
import static com.srmecotech.plantride.support.Fixtures.rider;
import static com.srmecotech.plantride.support.Fixtures.route1;
import static com.srmecotech.plantride.support.Fixtures.trip;
import static com.srmecotech.plantride.support.Fixtures.vendorCab;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MatchingEngineTest {

    @Mock
    DutyRepository dutyRepository;
    @Mock
    TripRepository tripRepository;
    @Mock
    BookingRepository bookingRepository;
    @Mock
    SettingsService settings;

    private MatchingEngine engine;
    private final RoutePlan route1 = route1(3);
    private final List<Duty> duties = new ArrayList<>();
    private final List<Trip> openTrips = new ArrayList<>();
    private final List<Booking> ridersOnTrips = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(settings.getInt(anyString(), anyInt())).thenAnswer(inv -> inv.getArgument(1));
        ComplianceService compliance = new ComplianceService(CLOCK, settings);
        EtaCalculator eta = new EtaCalculator(properties(), CLOCK);
        engine = new MatchingEngine(dutyRepository, tripRepository, bookingRepository, compliance, eta, properties(), CLOCK);
        when(dutyRepository.findActiveWithVehicleAndDriver()).thenReturn(duties);
        when(tripRepository.findOpenTrips()).thenReturn(openTrips);
        when(bookingRepository.findOnTrips(anyCollection(), anyCollection())).thenReturn(ridersOnTrips);
    }

    private MatchRequest shared(int from, int to) {
        return new MatchRequest(route1, from, to, 1, RideType.SHARED, null, null);
    }

    @Test
    @DisplayName("Hard rule: a cab already carrying 3 riders on the stretch is dropped before scoring")
    void capIsAHardFilter() {
        Vehicle c12 = cab(1, "C-12", GATE2);
        Driver kumar = driver(1, "S. Kumar");
        duties.add(duty(1, kumar, c12, 60));
        Trip t = trip(10, route1.route(), c12, kumar, 1, 1);
        openTrips.add(t);
        ridersOnTrips.addAll(List.of(rider(1, t, 1, 3, BookingStatus.ONBOARD), rider(2, t, 1, 4, BookingStatus.ONBOARD),
                rider(3, t, 1, 4, BookingStatus.ONBOARD)));

        MatchOutcome outcome = engine.evaluate(shared(1, 4));

        assertThat(outcome.feasible()).isEmpty();
        assertThat(outcome.rejected()).singleElement()
                .satisfies(r -> assertThat(r.describe()).contains("C-12 is at its 3-rider cap (3 of 3"));
    }

    @Test
    @DisplayName("A seat that frees up at a drop can be reused after it (cap is per segment)")
    void seatReusedAfterDrop() {
        Vehicle c12 = cab(1, "C-12", GATE2);
        Driver kumar = driver(1, "S. Kumar");
        duties.add(duty(1, kumar, c12, 60));
        Trip t = trip(10, route1.route(), c12, kumar, 1, 1);
        openTrips.add(t);
        ridersOnTrips.addAll(List.of(rider(1, t, 1, 3, BookingStatus.ONBOARD), rider(2, t, 1, 4, BookingStatus.ONBOARD),
                rider(3, t, 1, 4, BookingStatus.ONBOARD)));

        // Coke Plant (3) -> Blast Furnace (4): only 2 riders remain on that segment.
        MatchOutcome outcome = engine.evaluate(shared(3, 4));

        assertThat(outcome.best()).hasValueSatisfying(c -> {
            assertThat(c.vehicle().getCode()).isEqualTo("C-12");
            assertThat(c.seatsTaken()).isEqualTo(2);
            assertThat(c.seatsFree()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("Pooling wins a tie: joining a cab with riders beats an equally close empty cab")
    void poolingPreferred() {
        Vehicle c12 = cab(1, "C-12", GATE2);
        Vehicle c15 = cab(2, "C-15", GATE2);
        Driver kumar = driver(1, "S. Kumar");
        Driver joshi = driver(2, "K. Joshi");
        duties.add(duty(1, kumar, c12, 60));
        duties.add(duty(2, joshi, c15, 60));
        Trip t = trip(10, route1.route(), c12, kumar, 1, null);
        openTrips.add(t);
        ridersOnTrips.add(rider(1, t, 1, 4, BookingStatus.ASSIGNED));

        MatchOutcome outcome = engine.evaluate(shared(1, 4));

        assertThat(outcome.feasible()).extracting(c -> c.vehicle().getCode()).containsExactly("C-12", "C-15");
        assertThat(outcome.best().orElseThrow().trip()).isSameAs(t);
        assertThat(outcome.best().orElseThrow().reason()).contains("1 of 3 seats taken");
    }

    @Test
    @DisplayName("The nearer idle cab wins; an own-fleet cab beats a vendor cab at the same distance")
    void nearerAndOwnFleetPreferred() {
        Vehicle near = cab(1, "C-12", GATE2);
        Vehicle far = cab(2, "C-18", TOWNSHIP);
        Vehicle vendor = vendorCab(3, "C-07", GATE2);
        duties.add(duty(1, driver(1, "A"), far, 60));
        duties.add(duty(2, driver(2, "B"), vendor, 60));
        duties.add(duty(3, driver(3, "C"), near, 60));

        MatchOutcome outcome = engine.evaluate(shared(1, 4));

        assertThat(outcome.feasible()).extracting(c -> c.vehicle().getCode()).containsExactly("C-12", "C-07", "C-18");
        Candidate farCab = outcome.feasible().get(2);
        assertThat(farCab.etaMinutes()).isGreaterThan(3);
        assertThat(farCab.emptyKm()).isGreaterThan(1.0);
    }

    @Test
    @DisplayName("Shuttles, off-road cabs, expired documents, other routes and tired drivers are all filtered")
    void hardFilters() {
        Vehicle shuttle = cab(1, "S-03", ADMIN);
        shuttle.setVehicleType(VehicleType.SHUTTLE);
        Vehicle offRoad = cab(2, "C-44", GATE2);
        offRoad.setStatus(VehicleStatus.OFF_ROAD);
        Vehicle expired = cab(3, "C-09", GATE2);
        expired.setInsuranceExpiry(TODAY.minusDays(2));
        Vehicle busy = cab(4, "C-07", GATE2);
        Vehicle tired = cab(5, "C-21", ADMIN);
        Driver gatePassExpired = driver(6, "K. Joshi");
        gatePassExpired.setGatePassExpiry(TODAY.minusDays(3));
        Vehicle blockedDriverCab = cab(6, "C-15", GATE2);

        duties.add(duty(1, driver(1, "R. Das"), shuttle, 60));
        duties.add(duty(2, driver(2, "B. Mehta"), offRoad, 60));
        duties.add(duty(3, driver(3, "M. Ali"), expired, 60));
        Driver nair = driver(4, "P. Nair");
        duties.add(duty(4, nair, busy, 60));
        openTrips.add(trip(40, otherRoute(), busy, nair, 1, 1));
        duties.add(duty(5, driver(5, "A. Singh"), tired, 532));
        duties.add(duty(6, gatePassExpired, blockedDriverCab, 10));

        MatchOutcome outcome = engine.evaluate(shared(1, 4));

        assertThat(outcome.feasible()).isEmpty();
        assertThat(outcome.rejected()).extracting(Rejection::describe).satisfiesExactlyInAnyOrder(
                r -> assertThat(r).startsWith("S-03 runs the shuttle timetable"),
                r -> assertThat(r).startsWith("C-44 is off-road"),
                r -> assertThat(r).contains("C-09 vehicle insurance expired"),
                r -> assertThat(r).startsWith("C-07 is on Route 2"),
                r -> assertThat(r).contains("A. Singh would pass the duty-hour limit (8h52m of 9h00m"),
                r -> assertThat(r).contains("K. Joshi's gate pass expired"));
    }

    @Test
    @DisplayName("Exclusive rides only take an empty cab and may use every seat")
    void exclusiveNeedsEmptyCab() {
        Vehicle c12 = cab(1, "C-12", GATE2);
        Vehicle c15 = cab(2, "C-15", GATE2);
        Driver kumar = driver(1, "S. Kumar");
        duties.add(duty(1, kumar, c12, 60));
        duties.add(duty(2, driver(2, "X"), c15, 60));
        Trip t = trip(10, route1.route(), c12, kumar, 1, null);
        openTrips.add(t);
        ridersOnTrips.add(rider(1, t, 1, 4, BookingStatus.ASSIGNED));

        MatchOutcome outcome = engine.evaluate(new MatchRequest(route1, 1, 4, 4, RideType.EXCLUSIVE, null, null));

        assertThat(outcome.feasible()).singleElement().satisfies(c -> {
            assertThat(c.vehicle().getCode()).isEqualTo("C-15");
            assertThat(c.capacity()).isEqualTo(4);
        });
        assertThat(outcome.rejected()).singleElement()
                .satisfies(r -> assertThat(r.describe()).contains("an exclusive ride needs an empty cab"));
    }

    @Test
    @DisplayName("A cab that has already passed the pickup is not offered")
    void alreadyPassedPickup() {
        Vehicle c12 = cab(1, "C-12", GATE2);
        Driver kumar = driver(1, "S. Kumar");
        duties.add(duty(1, kumar, c12, 60));
        Trip t = trip(10, route1.route(), c12, kumar, 1, 3);
        openTrips.add(t);
        ridersOnTrips.add(rider(1, t, 1, 4, BookingStatus.ONBOARD));

        MatchOutcome outcome = engine.evaluate(shared(2, 4));

        assertThat(outcome.feasible()).isEmpty();
        assertThat(outcome.rejected().get(0).describe()).contains("has already passed Admin Block");
    }
}
