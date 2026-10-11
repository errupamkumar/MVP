package com.srmecotech.ecoride.admin;

import com.srmecotech.ecoride.common.audit.AuditService;
import com.srmecotech.ecoride.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Dashboard, live map, rides, safety alerts and the audit log. */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin · operations")
public class AdminOperationsController {

    private final AdminQueryService queries;
    private final AdminCommandService commands;
    private final AuditService audit;

    public AdminOperationsController(AdminQueryService queries, AdminCommandService commands, AuditService audit) {
        this.queries = queries;
        this.commands = commands;
        this.audit = audit;
    }

    @GetMapping("/me")
    public AdminDtos.AdminProfile me(@AuthenticationPrincipal AuthenticatedUser admin) {
        return commands.me(admin.id());
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Today's KPIs, 14-day trend, hourly demand, product mix, alerts and approvals waiting")
    public AdminDtos.Dashboard dashboard() {
        return queries.dashboard();
    }

    @GetMapping("/live")
    @Operation(summary = "Every online driver and every ride in flight, with surge zones (poll every few seconds)")
    public AdminDtos.Live live() {
        return queries.live();
    }

    @GetMapping("/rides")
    public AdminDtos.PageDto<AdminDtos.RideRow> rides(@RequestParam(required = false) String q,
                                                      @RequestParam(required = false) String status,
                                                      @RequestParam(required = false) String vehicleType,
                                                      @RequestParam(required = false) Long riderId,
                                                      @RequestParam(required = false) Long driverId,
                                                      @RequestParam(defaultValue = "true") boolean includeSimulated,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "25") int size) {
        return queries.rides(q, status, vehicleType, riderId, driverId, includeSimulated, Math.max(0, page),
                Math.min(100, Math.max(1, size)));
    }

    @GetMapping("/rides/{id}")
    @Operation(summary = "Everything about a ride: timeline, dispatch attempts, planned route vs GPS trace, fare, payments")
    public AdminDtos.RideDetail ride(@PathVariable long id) {
        return queries.ride(id);
    }

    @PostMapping("/rides/{id}/cancel")
    public AdminDtos.RideDetail cancel(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable long id,
                                       @Valid @RequestBody AdminDtos.ReasonRequest request) {
        commands.cancelRide(admin, id, request.reason());
        return queries.ride(id);
    }

    @PostMapping("/rides/{id}/refund")
    @Operation(summary = "Refund to the rider's wallet")
    public AdminDtos.RideDetail refund(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable long id,
                                       @Valid @RequestBody AdminDtos.AmountRequest request) {
        commands.refund(admin, id, request.amount(), request.note());
        return queries.ride(id);
    }

    @GetMapping("/sos")
    public List<AdminDtos.SosRow> sos(@RequestParam(required = false) String status) {
        return queries.sosRows(status == null || status.isBlank() ? null : status, 100);
    }

    @PostMapping("/sos/{id}/resolve")
    public List<AdminDtos.SosRow> resolveSos(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable long id,
                                             @RequestBody(required = false) Map<String, String> body) {
        commands.resolveSos(admin, id, body == null ? null : body.get("note"));
        return queries.sosRows(null, 100);
    }

    @GetMapping("/audit")
    public List<AuditService.AuditEntry> audit(@RequestParam(defaultValue = "200") int limit) {
        return audit.recent(Math.min(500, Math.max(1, limit)));
    }
}
