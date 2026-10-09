package com.srmecotech.plantride.matching;

import com.srmecotech.plantride.masterdata.RoutePlan;
import com.srmecotech.plantride.masterdata.Vehicle;
import com.srmecotech.plantride.support.Fixtures;
import com.srmecotech.plantride.trip.Trip;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.srmecotech.plantride.support.Fixtures.GATE2;
import static com.srmecotech.plantride.support.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

/** A cab starts from the last stop it confirmed, unless its driver's live GPS shows it has driven away. */
class EtaCalculatorTest {

    /** About 370 m from Gate 2, on the road to Admin Block. */
    private static final double AWAY_LAT = 18.6240;
    private static final double AWAY_LNG = 73.8003;

    private final EtaCalculator eta = new EtaCalculator(Fixtures.properties(), Fixtures.CLOCK);
    private final RoutePlan route1 = Fixtures.route1(3);

    @Test
    @DisplayName("Parked at its stop: the stop is the start point")
    void parkedAtStop() {
        Vehicle cab = Fixtures.cab(1, "C-12", GATE2);
        cab.setLastPingAt(NOW);

        assertThat(eta.reposition(cab, route1, 1).minutes()).isZero();
        assertThat(eta.roadKmTo(cab, GATE2)).isZero();
    }

    @Test
    @DisplayName("Live GPS away from the last confirmed stop: the live position is the start point")
    void liveGpsAwayFromStop() {
        Vehicle cab = awayFromGate2(NOW.minusSeconds(20));

        assertThat(eta.reposition(cab, route1, 1).minutes()).isBetween(2, 4);
        assertThat(eta.roadKmTo(cab, GATE2)).isBetween(0.4, 0.6);
    }

    @Test
    @DisplayName("A stale fix never overrides the last confirmed stop")
    void staleGpsIgnored() {
        Vehicle cab = awayFromGate2(NOW.minusMinutes(10));

        assertThat(eta.reposition(cab, route1, 1).minutes()).isZero();
        assertThat(eta.roadKmTo(cab, GATE2)).isZero();
    }

    @Test
    @DisplayName("A trip's first pickup counts down from the live position, not from the time the trip was created")
    void plannedTripFromLivePosition() {
        Vehicle cab = awayFromGate2(NOW);
        Trip trip = Fixtures.trip(1, route1.route(), cab, Fixtures.driver(1, "S. Kumar"), 1, null);
        trip.setCreatedAt(NOW.minusMinutes(10));

        // From the stop the cab would be "arriving now"; from where it really is, it is a few minutes out.
        assertThat(eta.minutesUntil(trip, route1, 1, List.of(1, 3))).isBetween(2, 4);
    }

    private static Vehicle awayFromGate2(java.time.LocalDateTime pingAt) {
        Vehicle cab = Fixtures.cab(1, "C-12", GATE2);
        cab.setLastLatitude(AWAY_LAT);
        cab.setLastLongitude(AWAY_LNG);
        cab.setLastPingAt(pingAt);
        return cab;
    }
}
