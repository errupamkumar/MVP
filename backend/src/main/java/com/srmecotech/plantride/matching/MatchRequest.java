package com.srmecotech.plantride.matching;

import com.srmecotech.plantride.booking.Booking;
import com.srmecotech.plantride.booking.RideType;
import com.srmecotech.plantride.masterdata.RoutePlan;

/**
 * @param onlyVehicleId   evaluate a single vehicle (desk override); null = whole fleet
 * @param ignoreBookingId leave this booking out of seat loads (it is being re-matched)
 */
public record MatchRequest(RoutePlan plan, int fromSeq, int toSeq, int seats, RideType rideType,
                           Long onlyVehicleId, Long ignoreBookingId) {

    public static MatchRequest forBooking(RoutePlan plan, Booking booking) {
        return new MatchRequest(plan, booking.getFromSeq(), booking.getToSeq(), booking.getSeats(),
                booking.getRideType(), null, booking.getId());
    }

    public MatchRequest onlyVehicle(Long vehicleId) {
        return new MatchRequest(plan, fromSeq, toSeq, seats, rideType, vehicleId, ignoreBookingId);
    }
}
