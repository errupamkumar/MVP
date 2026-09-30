package com.srmecotech.plantride.matching;

import com.srmecotech.plantride.booking.Booking;
import com.srmecotech.plantride.booking.BookingStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.srmecotech.plantride.support.Fixtures.rider;
import static org.assertj.core.api.Assertions.assertThat;

class SeatLoadTest {

    @Test
    @DisplayName("Load is counted per segment: a rider occupies pickup up to, not including, the drop")
    void segmentLoad() {
        List<Booking> riders = List.of(
                rider(1, null, 1, 3, BookingStatus.ONBOARD),   // Gate 2 -> Coke Plant
                rider(2, null, 1, 4, BookingStatus.ONBOARD),   // Gate 2 -> Blast Furnace
                rider(3, null, 2, 4, BookingStatus.ASSIGNED)); // Admin -> Blast Furnace
        assertThat(SeatLoad.loadOnSegment(riders, 1)).isEqualTo(2);
        assertThat(SeatLoad.loadOnSegment(riders, 2)).isEqualTo(3);
        assertThat(SeatLoad.loadOnSegment(riders, 3)).isEqualTo(2);
        assertThat(SeatLoad.loadOnSegment(riders, 4)).isZero();
    }

    @Test
    @DisplayName("Drop 1, pick up 1 at the same stop never counts as four in a three-seat cab")
    void dropThenPickupAtSameStop() {
        List<Booking> riders = List.of(
                rider(1, null, 1, 3, BookingStatus.ONBOARD),
                rider(2, null, 1, 4, BookingStatus.ONBOARD),
                rider(3, null, 1, 4, BookingStatus.ONBOARD));
        // Full from Gate 2 to Coke Plant, but one seat frees up at Coke Plant.
        assertThat(SeatLoad.maxLoad(riders, 1, 4)).isEqualTo(3);
        assertThat(SeatLoad.maxLoad(riders, 3, 4)).isEqualTo(2);
    }

    @Test
    @DisplayName("Group bookings count every seat")
    void groupSeats() {
        Booking group = rider(1, null, 1, 4, BookingStatus.ASSIGNED);
        group.setSeats(2);
        assertThat(SeatLoad.maxLoad(List.of(group), 1, 2)).isEqualTo(2);
    }
}
