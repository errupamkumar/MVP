package com.srmecotech.plantride.booking;

import com.srmecotech.plantride.booking.dto.RiderDtos.AvailabilityDto;
import com.srmecotech.plantride.booking.dto.RiderDtos.AvailabilityRequest;
import com.srmecotech.plantride.booking.dto.RiderDtos.BookingDto;
import com.srmecotech.plantride.booking.dto.RiderDtos.BookingSummaryDto;
import com.srmecotech.plantride.booking.dto.RiderDtos.CancelBookingRequest;
import com.srmecotech.plantride.booking.dto.RiderDtos.CreateBookingRequest;
import com.srmecotech.plantride.booking.dto.RiderDtos.EmployeeProfileDto;
import com.srmecotech.plantride.booking.dto.RiderDtos.RateBookingRequest;
import com.srmecotech.plantride.booking.dto.RiderDtos.RideOptionsRequest;
import com.srmecotech.plantride.booking.dto.RiderDtos.RideOptionsResponse;
import com.srmecotech.plantride.booking.dto.RiderDtos.RiderHomeDto;
import com.srmecotech.plantride.booking.dto.RiderDtos.RiderSosRequest;
import com.srmecotech.plantride.booking.dto.RiderDtos.SosResultDto;
import com.srmecotech.plantride.common.security.AuthenticatedUser;
import com.srmecotech.plantride.gps.dto.LiveLocationDtos.RiderLocationAckDto;
import com.srmecotech.plantride.gps.dto.LiveLocationDtos.RiderLocationRequest;
import com.srmecotech.plantride.masterdata.dto.MasterDataDtos.DestinationDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rider")
@Tag(name = "3. Rider")
public class RiderController {

    private final RiderBookingService service;

    public RiderController(RiderBookingService service) {
        this.service = service;
    }

    @GetMapping("/home")
    @Operation(summary = "Home screen: live booking, saved places, next shuttle, unread count")
    public RiderHomeDto home(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.home(user);
    }

    @GetMapping("/profile")
    @Operation(summary = "Employee details used to pre-fill the booking (from HRMS)")
    public EmployeeProfileDto profile(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.profile(user);
    }

    @GetMapping("/destinations")
    @Operation(summary = "Stops reachable from a pickup stop, with the fastest route to each")
    public List<DestinationDto> destinations(@RequestParam Long fromStopId) {
        return service.destinations(fromStopId);
    }

    @PostMapping("/ride-options")
    @Operation(summary = "Shuttle, shared and exclusive options with ETA and free seats")
    public RideOptionsResponse rideOptions(@AuthenticationPrincipal AuthenticatedUser user,
                                           @Valid @RequestBody RideOptionsRequest request) {
        return service.rideOptions(user, request);
    }

    @PostMapping("/availability")
    @Operation(summary = "When a ride can reach the pickup, cabs on duty with driver, seats and distance, and nearby pickup points")
    public AvailabilityDto availability(@AuthenticationPrincipal AuthenticatedUser user,
                                        @Valid @RequestBody AvailabilityRequest request) {
        return service.availability(user, request);
    }

    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Book a ride. Send an Idempotency-Key so a retried tap never books twice")
    public BookingDto create(@AuthenticationPrincipal AuthenticatedUser user,
                             @Parameter(description = "Client-generated UUID, one per booking attempt")
                             @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                             @Valid @RequestBody CreateBookingRequest request) {
        return service.create(user, request, idempotencyKey);
    }

    @GetMapping("/bookings")
    @Operation(summary = "My trips: scope = upcoming | past | cancelled")
    public List<BookingSummaryDto> list(@AuthenticationPrincipal AuthenticatedUser user,
                                        @RequestParam(defaultValue = "upcoming") String scope) {
        return service.list(user, scope);
    }

    @GetMapping("/bookings/{id}")
    @Operation(summary = "One booking with vehicle, driver contact, OTP, live progress and charge")
    public BookingDto get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return service.get(user, id);
    }

    @GetMapping("/bookings/{id}/availability")
    @Operation(summary = "Availability for a waiting booking, with search progress (time searching, next check, expiry)")
    public AvailabilityDto bookingAvailability(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return service.bookingAvailability(user, id);
    }

    @PostMapping("/bookings/{id}/search")
    @Operation(summary = "Search again now: try to match a waiting booking immediately instead of at the next sweep")
    public BookingDto searchAgain(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return service.searchAgain(user, id);
    }

    @PostMapping("/bookings/{id}/cancel")
    @Operation(summary = "Cancel any time before boarding")
    public BookingDto cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                             @Valid @RequestBody(required = false) CancelBookingRequest request) {
        return service.cancel(user, id, request == null ? null : request.reason());
    }

    @PostMapping("/bookings/{id}/rating")
    @Operation(summary = "Rate a completed ride (feeds the vendor SLA report)")
    public BookingDto rate(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                           @Valid @RequestBody RateBookingRequest request) {
        return service.rate(user, id, request);
    }

    @PostMapping("/bookings/{id}/location")
    @Operation(summary = "Share where I am with the driver coming to pick me up (only while the cab is on its way)")
    public RiderLocationAckDto shareLocation(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                                             @Valid @RequestBody RiderLocationRequest request) {
        return service.shareLocation(user, id, request);
    }

    @DeleteMapping("/bookings/{id}/location")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Stop sharing my location: it is deleted and the driver no longer sees it")
    public void stopSharingLocation(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        service.stopSharingLocation(user, id);
    }

    @PostMapping("/sos")
    @Operation(summary = "SOS: alerts the desk and plant security with the cab's live location")
    public SosResultDto sos(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody RiderSosRequest request) {
        return service.sos(user, request);
    }
}
