package com.srmecotech.plantride.booking;

import com.srmecotech.plantride.booking.dto.RiderDtos.RideProgressDto;
import com.srmecotech.plantride.booking.dto.RiderDtos.TrackingDto;
import com.srmecotech.plantride.common.error.ApiException;
import com.srmecotech.plantride.common.error.NotFoundException;
import com.srmecotech.plantride.trip.Trip;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * The public, no-login tracking link. It shows the vehicle and its progress
 * and never a name or phone number, and it stops working when the ride closes.
 */
@Service
public class TrackingService {

    private final BookingRepository bookingRepository;
    private final RideProgressService progressService;
    private final Clock clock;

    public TrackingService(BookingRepository bookingRepository, RideProgressService progressService, Clock clock) {
        this.bookingRepository = bookingRepository;
        this.progressService = progressService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TrackingDto track(String token) {
        if (token == null || !token.matches("[A-Za-z0-9]{4,16}")) {
            throw new NotFoundException("This tracking link is not valid.");
        }
        Booking booking = bookingRepository.findByTrackingToken(token)
                .orElseThrow(() -> new NotFoundException("This tracking link is not valid."));
        if (BookingStatus.CLOSED.contains(booking.getStatus())) {
            throw new ApiException(HttpStatus.GONE, "LINK_EXPIRED", "This trip has ended, so the tracking link has expired.");
        }
        Trip trip = booking.getTrip();
        RideProgressDto progress = progressService.progress(booking);
        return new TrackingDto(
                booking.getTrackingToken(),
                progress.stage(),
                progress.stageLabel(),
                trip == null ? null : trip.getVehicle().getCode(),
                trip == null ? null : trip.getVehicle().getRegistrationNo(),
                booking.getRoute().label(),
                booking.getFromStop().getName(),
                booking.getToStop().getName(),
                progress.vehicleAt(),
                progress.etaToPickupMinutes(),
                progress.etaToDropMinutes(),
                progress.stops(),
                LocalDateTime.now(clock));
    }
}
