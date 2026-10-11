package com.srmecotech.ecoride.admin;

import com.srmecotech.ecoride.common.security.AuthenticatedUser;
import com.srmecotech.ecoride.driver.Availability;
import com.srmecotech.ecoride.driver.DriverStatus;
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

import java.util.Map;

/** Drivers (onboarding approvals, suspensions, payouts) and riders (blocks, wallet credits). */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin · drivers and riders")
public class AdminPeopleController {

    private final AdminQueryService queries;
    private final AdminCommandService commands;

    public AdminPeopleController(AdminQueryService queries, AdminCommandService commands) {
        this.queries = queries;
        this.commands = commands;
    }

    // ------------------------------------------------------------------ drivers

    @GetMapping("/drivers")
    public AdminDtos.PageDto<AdminDtos.DriverRow> drivers(@RequestParam(required = false) String q,
                                                          @RequestParam(required = false) DriverStatus status,
                                                          @RequestParam(required = false) Availability availability,
                                                          @RequestParam(defaultValue = "true") boolean includeSimulated,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "25") int size) {
        return queries.drivers(q, status, availability, includeSimulated, Math.max(0, page), Math.min(100, Math.max(1, size)));
    }

    @GetMapping("/drivers/counts")
    public Map<String, Long> driverCounts() {
        return queries.driverCounts();
    }

    @GetMapping("/drivers/{id}")
    public AdminDtos.DriverDetail driver(@PathVariable long id) {
        return queries.driver(id);
    }

    @PostMapping("/drivers/{id}/approve")
    @Operation(summary = "Approve a driver application: they can go online at once")
    public AdminDtos.DriverDetail approve(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable long id) {
        commands.approveDriver(admin, id);
        return queries.driver(id);
    }

    @PostMapping("/drivers/{id}/reject")
    public AdminDtos.DriverDetail reject(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable long id,
                                         @Valid @RequestBody AdminDtos.ReasonRequest request) {
        commands.rejectDriver(admin, id, request.reason());
        return queries.driver(id);
    }

    @PostMapping("/drivers/{id}/suspend")
    public AdminDtos.DriverDetail suspend(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable long id,
                                          @Valid @RequestBody AdminDtos.ReasonRequest request) {
        commands.suspendDriver(admin, id, request.reason());
        return queries.driver(id);
    }

    @PostMapping("/drivers/{id}/reactivate")
    public AdminDtos.DriverDetail reactivate(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable long id) {
        commands.reactivateDriver(admin, id);
        return queries.driver(id);
    }

    @PostMapping("/drivers/{id}/offline")
    public AdminDtos.DriverDetail offline(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable long id) {
        commands.forceOffline(admin, id);
        return queries.driver(id);
    }

    @PostMapping("/drivers/{id}/payout")
    @Operation(summary = "Pay out the driver's balance (simulated bank transfer)")
    public AdminDtos.DriverDetail payout(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable long id) {
        commands.payout(admin, id);
        return queries.driver(id);
    }

    // ------------------------------------------------------------------ riders

    @GetMapping("/riders")
    public AdminDtos.PageDto<AdminDtos.RiderRow> riders(@RequestParam(required = false) String q,
                                                        @RequestParam(defaultValue = "true") boolean includeSimulated,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "25") int size) {
        return queries.riders(q, includeSimulated, Math.max(0, page), Math.min(100, Math.max(1, size)));
    }

    @GetMapping("/riders/counts")
    public Map<String, Long> riderCounts() {
        return queries.riderCounts();
    }

    @GetMapping("/riders/{id}")
    public AdminDtos.RiderDetail rider(@PathVariable long id) {
        return queries.rider(id);
    }

    @PostMapping("/riders/{id}/block")
    public AdminDtos.RiderDetail block(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable long id,
                                       @Valid @RequestBody AdminDtos.ReasonRequest request) {
        commands.blockRider(admin, id, request.reason());
        return queries.rider(id);
    }

    @PostMapping("/riders/{id}/unblock")
    public AdminDtos.RiderDetail unblock(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable long id) {
        commands.unblockRider(admin, id);
        return queries.rider(id);
    }

    @PostMapping("/riders/{id}/wallet-credit")
    public AdminDtos.RiderDetail credit(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable long id,
                                        @Valid @RequestBody AdminDtos.AmountRequest request) {
        commands.creditWallet(admin, id, request.amount(), request.note());
        return queries.rider(id);
    }
}
