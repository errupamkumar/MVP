package com.srmecotech.plantride.desk;

import com.srmecotech.plantride.common.security.AuthenticatedUser;
import com.srmecotech.plantride.desk.dto.DeskDtos.AssignRequest;
import com.srmecotech.plantride.desk.dto.DeskDtos.CandidateDto;
import com.srmecotech.plantride.desk.dto.DeskDtos.DeskActionResultDto;
import com.srmecotech.plantride.desk.dto.DeskDtos.LiveBoardDto;
import com.srmecotech.plantride.desk.dto.DeskDtos.QueueItemDto;
import com.srmecotech.plantride.desk.dto.DeskDtos.ReasonRequest;
import com.srmecotech.plantride.desk.dto.DeskDtos.VehicleStatusRequest;
import com.srmecotech.plantride.safety.AlertService;
import com.srmecotech.plantride.safety.dto.AlertDto;
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
import java.util.Map;

@RestController
@RequestMapping("/api/desk")
@Tag(name = "5. Transport desk")
public class DeskController {

    private final DeskService deskService;
    private final AlertService alertService;

    public DeskController(DeskService deskService, AlertService alertService) {
        this.deskService = deskService;
        this.alertService = alertService;
    }

    @GetMapping("/board")
    @Operation(summary = "Live board: KPIs, every vehicle's state, open alerts and the waiting queue")
    public LiveBoardDto board() {
        return deskService.board();
    }

    @GetMapping("/queue")
    @Operation(summary = "Bookings waiting for a cab or for approval")
    public List<QueueItemDto> queue() {
        return deskService.queue();
    }

    @GetMapping("/alerts")
    @Operation(summary = "Open and acknowledged alerts, most severe first")
    public List<AlertDto> alerts() {
        return alertService.unresolved();
    }

    @PostMapping("/alerts/{id}/ack")
    @Operation(summary = "Acknowledge an alert")
    public AlertDto acknowledge(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return alertService.acknowledge(id, user);
    }

    @PostMapping("/alerts/{id}/resolve")
    @Operation(summary = "Resolve an alert")
    public AlertDto resolve(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return alertService.resolve(id, user);
    }

    @GetMapping("/bookings/{id}/candidates")
    @Operation(summary = "Why each on-duty cab can or cannot take this booking (the engine's audit trail)")
    public List<CandidateDto> candidates(@PathVariable Long id) {
        return deskService.candidates(id);
    }

    @PostMapping("/bookings/{id}/approve")
    @Operation(summary = "Approve an exclusive ride")
    public DeskActionResultDto approve(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return deskService.approve(id, user);
    }

    @PostMapping("/bookings/{id}/reject")
    @Operation(summary = "Reject an exclusive ride")
    public DeskActionResultDto reject(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user,
                                      @Valid @RequestBody ReasonRequest request) {
        return deskService.reject(id, request.reason(), user);
    }

    @PostMapping("/bookings/{id}/assign")
    @Operation(summary = "Override: put a booking on a specific cab (hard rules and the cap still apply)")
    public DeskActionResultDto assign(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user,
                                      @Valid @RequestBody AssignRequest request) {
        return deskService.assign(id, request.vehicleId(), user);
    }

    @PostMapping("/bookings/{id}/cancel")
    @Operation(summary = "Cancel a booking on the rider's behalf")
    public DeskActionResultDto cancel(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user,
                                      @Valid @RequestBody ReasonRequest request) {
        return deskService.cancel(id, request.reason(), user);
    }

    @PostMapping("/vehicles/{id}/status")
    @Operation(summary = "Take a vehicle off-road (its riders are re-matched) or put it back on the road")
    public Map<String, String> vehicleStatus(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user,
                                             @Valid @RequestBody VehicleStatusRequest request) {
        return Map.of("message", deskService.setVehicleStatus(id, request.status(), request.note(), user));
    }
}
