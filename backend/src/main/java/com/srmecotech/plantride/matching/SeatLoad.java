package com.srmecotech.plantride.matching;

import com.srmecotech.plantride.booking.Booking;

import java.util.Collection;

/**
 * Seat occupancy along a route. Segment {@code s} is the stretch from
 * position s to s+1; a rider occupies every segment from their pickup up to
 * (not including) their drop. The cap applies to the busiest segment on the
 * new rider's stretch, so "drop 1, pick up 1" at the same stop never counts
 * as four people in a three-seat cab.
 */
public final class SeatLoad {

    private SeatLoad() {
    }

    public static int maxLoad(Collection<Booking> riders, int fromSeq, int toSeq) {
        int max = 0;
        for (int segment = fromSeq; segment < toSeq; segment++) {
            int load = loadOnSegment(riders, segment);
            max = Math.max(max, load);
        }
        return max;
    }

    public static int loadOnSegment(Collection<Booking> riders, int segment) {
        int load = 0;
        for (Booking b : riders) {
            if (b.getFromSeq() <= segment && segment < b.getToSeq()) {
                load += b.getSeats();
            }
        }
        return load;
    }
}
