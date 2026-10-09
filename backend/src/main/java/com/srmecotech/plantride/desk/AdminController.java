package com.srmecotech.plantride.desk;

import com.srmecotech.plantride.common.security.AuthenticatedUser;
import com.srmecotech.plantride.desk.dto.DeskDtos.AuditLogDto;
import com.srmecotech.plantride.desk.dto.DeskDtos.ComplianceItemDto;
import com.srmecotech.plantride.desk.dto.DeskDtos.PickupPointDto;
import com.srmecotech.plantride.desk.dto.DeskDtos.SavePickupPointRequest;
import com.srmecotech.plantride.desk.dto.DeskDtos.ResequenceRequest;
import com.srmecotech.plantride.desk.dto.DeskDtos.UpdateRouteRulesRequest;
import com.srmecotech.plantride.masterdata.dto.MasterDataDtos.RouteDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@Validated
@Tag(name = "6. Admin")
public class AdminController {

    private final AdminService adminService;
    private final PickupPointService pickupPointService;

    public AdminController(AdminService adminService, PickupPointService pickupPointService) {
        this.adminService = adminService;
        this.pickupPointService = pickupPointService;
    }

    @GetMapping("/routes")
    @Operation(summary = "All routes with stop sequence, geofences, leg times and rules")
    public List<RouteDto> routes() {
        return adminService.routes();
    }

    @PutMapping("/routes/{id}/rules")
    @Operation(summary = "Change the occupancy cap, wait, detour, speed limit or frequency (audited, no release)")
    public RouteDto updateRules(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user,
                                @Valid @RequestBody UpdateRouteRulesRequest request) {
        return adminService.updateRules(id, request, user);
    }

    @PutMapping("/routes/{id}/stops")
    @Operation(summary = "Replace the stop sequence (refused while the route has live rides)")
    public RouteDto resequence(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user,
                               @Valid @RequestBody ResequenceRequest request) {
        return adminService.resequence(id, request, user);
    }

    @GetMapping("/stops")
    @Operation(summary = "Every pickup point, in service first, with the routes that serve it and open bookings")
    public List<PickupPointDto> stops() {
        return pickupPointService.list();
    }

    @PostMapping("/stops")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a pickup point (then put it on a route in the route's stop sequence)")
    public PickupPointDto createStop(@AuthenticationPrincipal AuthenticatedUser user,
                                     @Valid @RequestBody SavePickupPointRequest request) {
        return pickupPointService.create(request, user);
    }

    @PutMapping("/stops/{id}")
    @Operation(summary = "Edit a pickup point, or take it out of service (refused while a route or open booking uses it)")
    public PickupPointDto updateStop(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user,
                                     @Valid @RequestBody SavePickupPointRequest request) {
        return pickupPointService.update(id, request, user);
    }

    @GetMapping("/compliance")
    @Operation(summary = "Documents expiring within the alert window or already expired")
    public List<ComplianceItemDto> compliance() {
        return adminService.compliance();
    }

    @GetMapping("/audit")
    @Operation(summary = "Who booked, changed, approved and overrode what")
    public List<AuditLogDto> audit(@RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return adminService.audit(limit);
    }

    @PostMapping("/demo/reset")
    @Operation(summary = "Reload the demo seed data (only when plantride.demo.reset-enabled=true)")
    public Map<String, String> resetDemo(@AuthenticationPrincipal AuthenticatedUser user) {
        return Map.of("message", adminService.resetDemo(user));
    }
}
