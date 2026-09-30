package com.srmecotech.plantride.booking;

import com.srmecotech.plantride.booking.dto.RiderDtos.BookingDto;
import com.srmecotech.plantride.booking.dto.RiderDtos.BookingSummaryDto;
import com.srmecotech.plantride.booking.dto.RiderDtos.DriverContactDto;
import com.srmecotech.plantride.common.config.AppProperties;
import com.srmecotech.plantride.masterdata.RoutePlan;
import com.srmecotech.plantride.masterdata.RouteResolver;
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
    private final String publicBaseUrl;

    public BookingMapper(RideProgressService progressService, RouteResolver routeResolver, AppProperties properties) {
        this.progressService = progressService;
        this.routeResolver = routeResolver;
        this.publicBaseUrl = properties.publicBaseUrl().replaceAll("/+$", "");
    }

    @Transactional(readOnly = true)
    public BookingDto toDto(Booking b) {
        Trip trip = b.getTrip();
        RoutePlan plan = routeResolver.planFor(b.getRoute().getId());
        boolean live = b.isActive();
        boolean showTrip = trip != null;
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
                showTrip ? new DriverContactDto(trip.getDriver().getUser().getFullName(), trip.getDriver().getUser().getPhone()) : null,
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

    private static List<String> tags(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
