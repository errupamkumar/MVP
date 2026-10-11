package com.srmecotech.ecoride.admin;

import com.srmecotech.ecoride.common.error.NotFoundException;
import com.srmecotech.ecoride.common.security.AuthenticatedUser;
import com.srmecotech.ecoride.common.settings.Setting;
import com.srmecotech.ecoride.common.settings.SettingsService;
import com.srmecotech.ecoride.demo.DemoResetService;
import com.srmecotech.ecoride.geo.GeoPoint;
import com.srmecotech.ecoride.pricing.FareBreakdown;
import com.srmecotech.ecoride.pricing.FareCalculator;
import com.srmecotech.ecoride.pricing.PromoCode;
import com.srmecotech.ecoride.pricing.PromoCodeRepository;
import com.srmecotech.ecoride.pricing.SurgeService;
import com.srmecotech.ecoride.pricing.SurgeZone;
import com.srmecotech.ecoride.pricing.SurgeZoneRepository;
import com.srmecotech.ecoride.pricing.VehicleType;
import com.srmecotech.ecoride.pricing.VehicleTypeRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Fare cards, surge, promotions, business settings, finance and the demo reset. */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin · pricing, settings and finance")
public class AdminConfigController {

    private final AdminCommandService commands;
    private final AdminQueryService queries;
    private final VehicleTypeRepository vehicleTypes;
    private final SurgeZoneRepository zones;
    private final SurgeService surge;
    private final PromoCodeRepository promos;
    private final SettingsService settings;
    private final DemoResetService demoReset;

    public AdminConfigController(AdminCommandService commands, AdminQueryService queries,
                                 VehicleTypeRepository vehicleTypes, SurgeZoneRepository zones, SurgeService surge,
                                 PromoCodeRepository promos, SettingsService settings, DemoResetService demoReset) {
        this.commands = commands;
        this.queries = queries;
        this.vehicleTypes = vehicleTypes;
        this.zones = zones;
        this.surge = surge;
        this.promos = promos;
        this.settings = settings;
        this.demoReset = demoReset;
    }

    // ------------------------------------------------------------------ fare cards

    @GetMapping("/vehicle-types")
    public List<AdminDtos.VehicleTypeDto> vehicleTypes() {
        return vehicleTypes.findAllByOrderBySortOrder().stream().map(AdminConfigController::toDto).toList();
    }

    @PutMapping("/vehicle-types/{code}")
    @Operation(summary = "Change a fare card; new quotes use it at once (rides already booked keep their price)")
    public AdminDtos.VehicleTypeDto updateVehicleType(@AuthenticationPrincipal AuthenticatedUser admin,
                                                      @PathVariable String code,
                                                      @Valid @RequestBody AdminDtos.VehicleTypeUpdate request) {
        return toDto(commands.updateVehicleType(admin, code, request));
    }

    @PostMapping("/fare-preview")
    @Operation(summary = "What a trip would cost with the current fare card")
    public FareBreakdown farePreview(@Valid @RequestBody AdminDtos.FarePreviewRequest request) {
        VehicleType vt = vehicleTypes.findById(request.vehicleType())
                .orElseThrow(() -> new NotFoundException("Vehicle type", request.vehicleType()));
        FareCalculator.Promo promo = request.promoCode() == null || request.promoCode().isBlank() ? null
                : promos.findById(request.promoCode().trim().toUpperCase(Locale.ROOT)).map(PromoCode::toPromo).orElse(null);
        return FareCalculator.calculate(vt, request.distanceKm() * 1000, request.minutes() * 60,
                request.surge() == null ? BigDecimal.ONE : request.surge(),
                (request.waitingMin() == null ? 0 : request.waitingMin()) * 60, promo, settings.getDouble(Setting.GST_PCT));
    }

    // ------------------------------------------------------------------ surge

    @GetMapping("/surge-zones")
    public List<AdminDtos.SurgeZoneDto> zones() {
        return zones.findAllByOrderByName().stream().map(this::toDto).toList();
    }

    @PostMapping("/surge-zones")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminDtos.SurgeZoneDto addZone(@AuthenticationPrincipal AuthenticatedUser admin,
                                          @Valid @RequestBody AdminDtos.SurgeZoneRequest request) {
        return toDto(commands.saveZone(admin, null, request));
    }

    @PutMapping("/surge-zones/{id}")
    public AdminDtos.SurgeZoneDto updateZone(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable long id,
                                             @Valid @RequestBody AdminDtos.SurgeZoneRequest request) {
        return toDto(commands.saveZone(admin, id, request));
    }

    @DeleteMapping("/surge-zones/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteZone(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable long id) {
        commands.deleteZone(admin, id);
    }

    // ------------------------------------------------------------------ promotions

    @GetMapping("/promos")
    public List<AdminDtos.PromoDto> promos() {
        return promos.findAllByOrderByCreatedAtDesc().stream().map(this::toDto).toList();
    }

    @PostMapping("/promos")
    @Operation(summary = "Create or update a promo code")
    public AdminDtos.PromoDto savePromo(@AuthenticationPrincipal AuthenticatedUser admin,
                                        @Valid @RequestBody AdminDtos.PromoRequest request) {
        return toDto(commands.savePromo(admin, request));
    }

    // ------------------------------------------------------------------ settings

    @GetMapping("/settings")
    public List<AdminDtos.SettingDto> settings() {
        return settings.all().entrySet().stream().map(e -> toDto(e.getKey(), e.getValue())).toList();
    }

    @PutMapping("/settings/{key}")
    public AdminDtos.SettingDto updateSetting(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable String key,
                                              @Valid @RequestBody AdminDtos.SettingUpdate request) {
        Setting setting;
        try {
            setting = Setting.valueOf(key);
        } catch (IllegalArgumentException ex) {
            throw new NotFoundException("Setting", key);
        }
        return toDto(setting, commands.updateSetting(admin, setting, request.value()));
    }

    // ------------------------------------------------------------------ money

    @GetMapping("/finance")
    @Operation(summary = "Revenue, commission, taxes, payment mix and driver payouts over a period")
    public AdminDtos.Finance finance(@RequestParam(defaultValue = "30") int days) {
        return queries.finance(Math.min(90, Math.max(1, days)));
    }

    @GetMapping("/payments")
    public AdminDtos.PageDto<AdminDtos.PaymentRow> payments(@RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "50") int size) {
        return queries.paymentsPage(Math.max(0, page), Math.min(200, Math.max(1, size)));
    }

    // ------------------------------------------------------------------ demo

    @PostMapping("/demo/reset")
    @Operation(summary = "Rebuild the demo data (all accounts, fares, 30 days of history). Every signed-in app must sign in again.")
    public Map<String, Object> reset(@AuthenticationPrincipal AuthenticatedUser admin) {
        long ms = demoReset.reset();
        return Map.of("ok", true, "tookMs", ms);
    }

    // ------------------------------------------------------------------ mapping

    private static AdminDtos.VehicleTypeDto toDto(VehicleType vt) {
        return new AdminDtos.VehicleTypeDto(vt.getCode(), vt.getName(), vt.getDescription(), vt.getCapacity(),
                vt.getBaseFare(), vt.getBaseKm(), vt.getPerKm(), vt.getPerMin(), vt.getMinFare(), vt.getWaitFreeMin(),
                vt.getWaitPerMin(), vt.getCancelFee(), vt.getSortOrder(), vt.isActive(), vt.getUpdatedAt());
    }

    private AdminDtos.SurgeZoneDto toDto(SurgeZone z) {
        SurgeService.SurgeInfo info = z.isActive() ? surge.forZone(z, new GeoPoint(z.getLat(), z.getLng())) : null;
        return new AdminDtos.SurgeZoneDto(z.getId(), z.getName(), z.getLat(), z.getLng(), z.getRadiusM(), z.getMode(),
                z.getManualMultiplier(), z.isActive(), info == null ? BigDecimal.ONE : info.multiplier(),
                info == null ? null : info.demand(), info == null ? null : info.supply(), z.getUpdatedAt());
    }

    private AdminDtos.PromoDto toDto(PromoCode p) {
        return new AdminDtos.PromoDto(p.getCode(), p.getDescription(), p.getPercentOff(), p.getFlatOff(),
                p.getMaxDiscount(), p.getMinFare(), p.getUsesPerRider(), p.getValidUntil(), p.isActive(),
                promos.totalUses(p.getCode()), p.getCreatedAt());
    }

    private static AdminDtos.SettingDto toDto(Setting s, String value) {
        return new AdminDtos.SettingDto(s.name(), s.category(), s.label(), s.description(), s.type().name(), value,
                s.defaultValue(), s.min(), s.max(), s.unit());
    }
}
