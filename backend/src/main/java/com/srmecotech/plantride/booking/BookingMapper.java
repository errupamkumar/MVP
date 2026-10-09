package com.srmecotech.plantride.booking;

import com.srmecotech.plantride.booking.dto.RiderDtos.BookingDto;
import com.srmecotech.plantride.booking.dto.RiderDtos.BookingSummaryDto;
import com.srmecotech.plantride.booking.dto.RiderDtos.DriverContactDto;
import com.srmecotech.plantride.common.config.AppProperties;
import com.srmecotech.plantride.gps.LiveLocationService;
import com.srmecotech.plantride.masterdata.RoutePlan;
import com.srmecotech.plantride.masterdata.RouteResolver;
import com.srmecotech.plantride.masterdata.Stop;
import com.srmecotech.plantride.masterdata.Vehicle;
import com.srmecotech.plantride.masterdata.dto.MasterDataDtos.RouteRef;
import com.srmecotech.plantride.masterdata.dto.MasterDataDtos.StopRef;
import com.srmecotech.plantride.masterdata.dto.MasterDataDtos.VehicleRef;
import com.srmecotech.plantride.trip.Trip;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Component
public class BookingMapper {

    private final RideProgressService progressService;
    private final RouteResolver routeResolver;
    private final LiveLocationService liveLocations;
    private final String publicBaseUrl;

    public BookingMapper(RideProgressService progressService, RouteResolver routeResolver,
                         LiveLocationService liveLocations, AppProperties properties) {
        this.progressService = progressService;
        this.routeResolver = routeResolver;
        this.liveLocations = liveLocations;
        this.publicBaseUrl = properties.publicBaseUrl().replaceAll("/+$", "");
    }

    @Transactional(readOnly = true)
    public BookingDto toDto(Booking b) {
        Trip trip = b.getTrip();
        RoutePlan plan = routeResolver.planFor(b.getRoute().getId());
        boolean live = b.isActive();
        boolean showTrip = trip != null;
        // The cab's live position matters from assignment until the drop: to the pickup, then to the drop.
        boolean tracking = showTrip && (b.getStatus() == BookingStatus.ASSIGNED || b.getStatus() == BookingStatus.ONBOARD);
        Stop towards = b.getStatus() == BookingStatus.ONBOARD ? plan.stopAt(b.getToSeq()) : plan.stopAt(b.getFromSeq());
        return new BookingDto(
                b.getId(),
                b.getBookingCode(),
                b.getStatus(),
                b.getRideType(),
                b.getSeats(),
                b.isForGuest(),
                b.getRiderName(),
                b.getRiderPhone(),
                b.getRiderEmail(),
                RouteRef.of(b.getRoute()),
                StopRef.of(b.getFromStop()),
                StopRef.of(b.getToStop()),
                b.getScheduledAt(),
                b.getCreatedAt(),
                b.getAssignedAt(),
                b.getBoardedAt(),
                b.getDroppedAt(),
                b.getCancelledAt(),
                b.getCancelReason(),
                live ? b.getOtp() : null,
                b.getTrackingToken(),
                publicBaseUrl + "/t/" + b.getTrackingToken(),
                showTrip ? VehicleRef.of(trip.getVehicle()) : null,
                showTrip ? driver(trip) : null,
                tracking ? liveLocations.cab(trip.getVehicle(), plan, towards) : null,
                liveLocations.sharedBy(b, plan),
                showTrip ? trip.getTripCode() : null,
                b.getEtaMinutes(),
                b.getPromisedPickupAt(),
                progressService.progress(b),
                b.getCostCentre().getCode(),
                b.getCostCentre().getName(),
                plan.kmBetween(b.getFromSeq(), b.getToSeq()),
                plan.minutesBetween(b.getFromSeq(), b.getToSeq()),
                b.getDistanceKm(),
                b.getCostAmount(),
                b.getMatchNote(),
                b.getRating(),
                tags(b.getRatingTags()),
                b.getRatingNote(),
                BookingStatus.CANCELLABLE.contains(b.getStatus()),
                b.getStatus() == BookingStatus.COMPLETED && b.getRating() == null);
    }

    public BookingSummaryDto toSummary(Booking b) {
        Trip trip = b.getTrip();
        return new BookingSummaryDto(
                b.getId(),
                b.getBookingCode(),
                b.getStatus(),
                b.getRideType(),
                b.getFromStop().getName(),
                b.getToStop().getName(),
                b.getScheduledAt() != null ? b.getScheduledAt() : b.getCreatedAt(),
                b.getDistanceKm(),
                trip == null ? null : trip.getVehicle().getCode(),
                b.getCostAmount(),
                b.getCancelReason(),
                b.getRating());
    }

    private static DriverContactDto driver(Trip trip) {
        Vehicle v = trip.getVehicle();
        return new DriverContactDto(trip.getDriver().getUser().getFullName(), trip.getDriver().getUser().getPhone(),
                v.isOwnFleet() ? "LHS own fleet" : v.getVendor().getName());
    }

    private static List<String> tags(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
