package com.srmecotech.plantride.desk;

import com.srmecotech.plantride.booking.BookingRepository;
import com.srmecotech.plantride.booking.BookingStatus;
import com.srmecotech.plantride.common.audit.AuditService;
import com.srmecotech.plantride.common.error.BusinessRuleException;
import com.srmecotech.plantride.common.error.InvalidRequestException;
import com.srmecotech.plantride.common.error.NotFoundException;
import com.srmecotech.plantride.common.security.AuthenticatedUser;
import com.srmecotech.plantride.common.util.GeoMath;
import com.srmecotech.plantride.desk.dto.DeskDtos.PickupPointDto;
import com.srmecotech.plantride.desk.dto.DeskDtos.SavePickupPointRequest;
import com.srmecotech.plantride.masterdata.Route;
import com.srmecotech.plantride.masterdata.RouteRepository;
import com.srmecotech.plantride.masterdata.RouteStop;
import com.srmecotech.plantride.masterdata.Stop;
import com.srmecotech.plantride.masterdata.StopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Pickup points: the places a ride can come to. The admin adds them, says
 * where exactly to wait, and takes them out of service; a point is put on a
 * route in the route's stop sequence. Every change is audited.
 */
@Service
public class PickupPointService {

    /** A new point further than this from every existing one is almost certainly a coordinate typo. */
    private static final double MAX_KM_FROM_NEAREST = 20d;

    private final StopRepository stopRepository;
    private final RouteRepository routeRepository;
    private final BookingRepository bookingRepository;
    private final AuditService auditService;

    public PickupPointService(StopRepository stopRepository, RouteRepository routeRepository,
                              BookingRepository bookingRepository, AuditService auditService) {
        this.stopRepository = stopRepository;
        this.routeRepository = routeRepository;
        this.bookingRepository = bookingRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<PickupPointDto> list() {
        Map<Long, Set<String>> routesByStop = routesByStop();
        return stopRepository.findAll().stream()
                .sorted(Comparator.comparing((Stop s) -> !s.isActive()).thenComparing(Stop::getName))
                .map(s -> toDto(s, routesByStop))
                .toList();
    }

    @Transactional
    public PickupPointDto create(SavePickupPointRequest request, AuthenticatedUser actor) {
        if (request.code() == null || request.code().isBlank()) {
            throw new InvalidRequestException("CODE_REQUIRED", "Enter a short code for the pickup point, e.g. GATE3.");
        }
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (stopRepository.findAll().stream().anyMatch(s -> s.getCode().equalsIgnoreCase(code))) {
            throw new BusinessRuleException("DUPLICATE_CODE", "Another pickup point already uses the code " + code + ".");
        }
        String name = request.name().trim();
        checkName(name, null);
        checkPlausible(request.latitude(), request.longitude(), null);

        Stop stop = new Stop();
        stop.setCode(code);
        apply(stop, request);
        stop.setActive(request.active() == null || request.active());
        stopRepository.saveAndFlush(stop);
        auditService.record(actor, "STOP_CREATED", "STOP", stop.getId(), describe(stop));
        return toDto(stop, routesByStop());
    }

    @Transactional
    public PickupPointDto update(Long id, SavePickupPointRequest request, AuthenticatedUser actor) {
        Stop stop = stopRepository.findById(id).orElseThrow(() -> new NotFoundException("Pickup point", id));
        String name = request.name().trim();
        checkName(name, id);
        checkPlausible(request.latitude(), request.longitude(), id);
        boolean active = request.active() == null ? stop.isActive() : request.active();
        Map<Long, Set<String>> routesByStop = routesByStop();
        if (stop.isActive() && !active) {
            Set<String> routes = routesByStop.getOrDefault(id, Set.of());
            if (!routes.isEmpty()) {
                throw new BusinessRuleException("STOP_ON_ROUTE", stop.getName() + " is on " + String.join(" and ", routes)
                        + ". Remove it from the route's stop sequence first.");
            }
            long live = bookingRepository.countUsingStop(id, BookingStatus.ACTIVE);
            if (live > 0) {
                throw new BusinessRuleException("STOP_IN_USE", live + " open booking(s) pick up or drop at "
                        + stop.getName() + ". Take it out of service when they are closed.");
            }
        }

        List<String> changes = new ArrayList<>();
        change(changes, "name", stop.getName(), name);
        change(changes, "zone", stop.getZone(), request.zone().trim().toUpperCase(Locale.ROOT));
        change(changes, "landmark", stop.getLandmark(), blankToNull(request.landmark()));
        change(changes, "position", position(stop.getLatitude(), stop.getLongitude()),
                position(request.latitude(), request.longitude()));
        change(changes, "arrival radius (m)", stop.getGeofenceM(), request.geofenceM());
        change(changes, "in service", stop.isActive(), active);
        if (changes.isEmpty()) {
            return toDto(stop, routesByStop);
        }
        apply(stop, request);
        stop.setActive(active);
        stopRepository.saveAndFlush(stop);
        auditService.record(actor, "STOP_UPDATED", "STOP", id, stop.getName() + ": " + String.join("; ", changes));
        return toDto(stop, routesByStop);
    }

    // ------------------------------------------------------------------ helpers

    private void apply(Stop stop, SavePickupPointRequest request) {
        stop.setName(request.name().trim());
        stop.setZone(request.zone().trim().toUpperCase(Locale.ROOT));
        stop.setLandmark(blankToNull(request.landmark()));
        stop.setLatitude(request.latitude());
        stop.setLongitude(request.longitude());
        stop.setGeofenceM(request.geofenceM());
    }

    private void checkName(String name, Long ownId) {
        boolean taken = stopRepository.findAll().stream()
                .anyMatch(s -> s.getName().equalsIgnoreCase(name) && !s.getId().equals(ownId));
        if (taken) {
            throw new BusinessRuleException("DUPLICATE_NAME", "Another pickup point is already called " + name + ".");
        }
    }

    /** Refuses a point implausibly far from every other one: almost always a slip in the coordinates. */
    private void checkPlausible(double lat, double lng, Long ownId) {
        double nearest = stopRepository.findAll().stream()
                .filter(s -> !s.getId().equals(ownId))
                .mapToDouble(s -> GeoMath.distanceKm(lat, lng, s.getLatitude(), s.getLongitude()))
                .min()
                .orElse(0);
        if (nearest > MAX_KM_FROM_NEAREST) {
            throw new InvalidRequestException("FAR_FROM_PLANT", String.format(Locale.ROOT,
                    "That position is %.0f km from the nearest pickup point. Check the latitude and longitude.", nearest));
        }
    }

    private Map<Long, Set<String>> routesByStop() {
        Map<Long, Set<String>> byStop = new LinkedHashMap<>();
        for (Route route : routeRepository.findAllWithStops()) {
            for (RouteStop rs : route.getStops()) {
                byStop.computeIfAbsent(rs.getStop().getId(), k -> new TreeSet<>()).add(route.label());
            }
        }
        return byStop;
    }

    private PickupPointDto toDto(Stop s, Map<Long, Set<String>> routesByStop) {
        return new PickupPointDto(s.getId(), s.getCode(), s.getName(), s.getZone(), s.getLandmark(), s.getLatitude(),
                s.getLongitude(), s.getGeofenceM(), s.isActive(), List.copyOf(routesByStop.getOrDefault(s.getId(), Set.of())),
                bookingRepository.countUsingStop(s.getId(), BookingStatus.ACTIVE));
    }

    private static String describe(Stop s) {
        return s.getCode() + " · " + s.getName() + " · " + s.getZone()
                + (s.getLandmark() == null ? "" : " · " + s.getLandmark())
                + " · " + position(s.getLatitude(), s.getLongitude()) + " · radius " + s.getGeofenceM() + " m";
    }

    private static String position(double lat, double lng) {
        return String.format(Locale.ROOT, "%.6f, %.6f", lat, lng);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void change(List<String> changes, String label, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            changes.add(label + " " + (before == null ? "none" : before) + " → " + (after == null ? "none" : after));
        }
    }
}
