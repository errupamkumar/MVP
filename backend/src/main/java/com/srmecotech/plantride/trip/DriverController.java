package com.srmecotech.plantride.trip;

import com.srmecotech.plantride.common.security.AuthenticatedUser;
import com.srmecotech.plantride.gps.GpsService;
import com.srmecotech.plantride.masterdata.Driver;
import com.srmecotech.plantride.trip.dto.DriverDtos.ArriveRequest;
import com.srmecotech.plantride.trip.dto.DriverDtos.BoardRequest;
import com.srmecotech.plantride.trip.dto.DriverDtos.DriverProfileDto;
import com.srmecotech.plantride.trip.dto.DriverDtos.DriverQueueResponse;
import com.srmecotech.plantride.trip.dto.DriverDtos.DutyDto;
import com.srmecotech.plantride.trip.dto.DriverDtos.DutySummaryDto;
import com.srmecotech.plantride.trip.dto.DriverDtos.EndDutyRequest;
import com.srmecotech.plantride.trip.dto.DriverDtos.IssueResultDto;
import com.srmecotech.plantride.trip.dto.DriverDtos.LocationAckDto;
import com.srmecotech.plantride.trip.dto.DriverDtos.LocationPingRequest;
import com.srmecotech.plantride.trip.dto.DriverDtos.NoShowRequest;
import com.srmecotech.plantride.trip.dto.DriverDtos.ReportIssueRequest;
import com.srmecotech.plantride.trip.dto.DriverDtos.SosRequest;
import com.srmecotech.plantride.trip.dto.DriverDtos.StartDutyRequest;
import com.srmecotech.plantride.trip.dto.DriverDtos.TripQueueDto;
import com.srmecotech.plantride.trip.dto.DriverDtos.VehicleOptionDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/driver")
@Tag(name = "4. Driver")
public class DriverController {

    private final DriverContext driverContext;
    private final DutyService dutyService;
    private final TripService tripService;
    private final IssueService issueService;
    private final GpsService gpsService;

    public DriverController(DriverContext driverContext, DutyService dutyService, TripService tripService,
                            IssueService issueService, GpsService gpsService) {
        this.driverContext = driverContext;
        this.dutyService = dutyService;
        this.tripService = tripService;
        this.issueService = issueService;
        this.gpsService = gpsService;
    }

    @GetMapping("/profile")
    @Operation(summary = "Driver profile, document validity and current duty")
    public DriverProfileDto profile(@AuthenticationPrincipal AuthenticatedUser user) {
        return dutyService.profile(user);
    }

    @GetMapping("/vehicles")
    @Operation(summary = "Vehicles this driver may sign on with, and why any are blocked")
    public List<VehicleOptionDto> vehicles(@AuthenticationPrincipal AuthenticatedUser user) {
        return dutyService.vehicleOptions(user);
    }

    @PostMapping("/duty/start")
    @Operation(summary = "Sign on: vehicle check, odometer, compliance gate")
    public DutyDto startDuty(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody StartDutyRequest request) {
        return dutyService.start(user, request);
    }

    @PostMapping("/duty/end")
    @Operation(summary = "Sign off: closing odometer, fuel, GPS-vs-odometer check")
    public DutySummaryDto endDuty(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody EndDutyRequest request) {
        return dutyService.end(user, request);
    }

    @GetMapping("/queue")
    @Operation(summary = "The sequenced trip queue (trip = null when there is no trip)")
    public DriverQueueResponse queue(@AuthenticationPrincipal AuthenticatedUser user) {
        Driver driver = driverContext.require(user);
        return wrap(tripService.currentQueue(driver), "New riders appear here automatically, in route order.");
    }

    @PostMapping("/trips/{tripId}/arrive")
    @Operation(summary = "Arrived at the next stop: drops complete, waiting riders are told the cab is here")
    public DriverQueueResponse arrive(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long tripId,
                                      @Valid @RequestBody ArriveRequest request) {
        Driver driver = driverContext.require(user);
        return wrap(tripService.arrive(driver, tripId, request.stopSeq()), "Trip complete. Every rider has been dropped.");
    }

    @PostMapping("/trips/{tripId}/board")
    @Operation(summary = "Board a rider with their 4-digit OTP")
    public DriverQueueResponse board(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long tripId,
                                     @Valid @RequestBody BoardRequest request) {
        Driver driver = driverContext.require(user);
        TripQueueDto queue = tripService.board(driver, tripId, request.bookingId(), request.otp());
        return new DriverQueueResponse(queue, null);
    }

    @PostMapping("/trips/{tripId}/no-show")
    @Operation(summary = "Mark a rider as a no-show after the configured wait")
    public DriverQueueResponse noShow(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long tripId,
                                      @Valid @RequestBody NoShowRequest request) {
        Driver driver = driverContext.require(user);
        return wrap(tripService.noShow(driver, user, tripId, request.bookingId()), "Trip closed. Nobody left to carry.");
    }

    @PostMapping("/location")
    @Operation(summary = "GPS ping from the driver app: live position, overspeed check, geofence arrival")
    public LocationAckDto location(@AuthenticationPrincipal AuthenticatedUser user,
                                   @Valid @RequestBody LocationPingRequest request) {
        return gpsService.ingest(user, request);
    }

    @PostMapping("/issues")
    @Operation(summary = "Report a breakdown, accident, delay, fuel entry or other issue")
    public IssueResultDto reportIssue(@AuthenticationPrincipal AuthenticatedUser user,
                                      @Valid @RequestBody ReportIssueRequest request) {
        return issueService.report(user, request);
    }

    @PostMapping("/sos")
    @Operation(summary = "SOS: alerts the transport desk and plant security with the cab's location")
    public IssueResultDto sos(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody SosRequest request) {
        return issueService.sos(user, request);
    }

    private static DriverQueueResponse wrap(Optional<TripQueueDto> queue, String noticeWhenEmpty) {
        return queue.map(q -> new DriverQueueResponse(q, null))
                .orElseGet(() -> new DriverQueueResponse(null, noticeWhenEmpty));
    }
}
