package com.srmecotech.plantride.trip;

import com.srmecotech.plantride.booking.Booking;
import com.srmecotech.plantride.booking.BookingStatus;
import com.srmecotech.plantride.masterdata.RoutePlan;
import com.srmecotech.plantride.masterdata.Vehicle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.srmecotech.plantride.support.Fixtures.GATE2;
import static com.srmecotech.plantride.support.Fixtures.cab;
import static com.srmecotech.plantride.support.Fixtures.driver;
import static com.srmecotech.plantride.support.Fixtures.rider;
import static com.srmecotech.plantride.support.Fixtures.route1;
import static com.srmecotech.plantride.support.Fixtures.trip;
import static org.assertj.core.api.Assertions.assertThat;

class TripItineraryTest {

    private final RoutePlan plan = route1(3);
    private final Vehicle c12 = cab(1, "C-12", GATE2);

    @Test
    @DisplayName("Heading to the first pickup: the next stop is the earliest pickup")
    void nextStopBeforeStart() {
        Trip trip = trip(1, plan.route(), c12, driver(1, "S. Kumar"), 1, null);
        List<Booking> active = List.of(rider(1, trip, 1, 3, BookingStatus.ASSIGNED), rider(2, trip, 2, 4, BookingStatus.ASSIGNED));
        assertThat(TripItinerary.nextStoppingSeq(trip, active)).isEqualTo(1);
        assertThat(TripItinerary.arrivalBlocker(trip, plan, active)).isEmpty();
    }

    @Test
    @DisplayName("Pass-through stops are skipped: next stop is the next pickup or drop")
    void skipsStopsWithNoAction() {
        Trip trip = trip(1, plan.route(), c12, driver(1, "S. Kumar"), 1, 1);
        List<Booking> active = List.of(rider(1, trip, 1, 4, BookingStatus.ONBOARD));
        assertThat(TripItinerary.nextStoppingSeq(trip, active)).isEqualTo(4);
    }

    @Test
    @DisplayName("The cab cannot move on while someone at the current stop has not boarded or been marked a no-show")
    void blocksLeavingWithRiderWaiting() {
        Trip trip = trip(1, plan.route(), c12, driver(1, "S. Kumar"), 1, 1);
        List<Booking> active = List.of(rider(1, trip, 1, 3, BookingStatus.ONBOARD), rider(2, trip, 1, 4, BookingStatus.ASSIGNED));
        assertThat(TripItinerary.arrivalBlocker(trip, plan, active))
                .hasValueSatisfying(msg -> assertThat(msg).contains("Rider 2").contains("Gate 2"));
    }

    @Test
    @DisplayName("Nothing left to do: no next stop")
    void noMoreStops() {
        Trip trip = trip(1, plan.route(), c12, driver(1, "S. Kumar"), 1, 4);
        assertThat(TripItinerary.nextStoppingSeq(trip, List.of())).isNull();
        assertThat(TripItinerary.arrivalBlocker(trip, plan, List.of())).isPresent();
    }
}
