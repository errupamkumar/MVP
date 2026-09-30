package com.srmecotech.plantride.desk;

import com.srmecotech.plantride.booking.BookingRepository;
import com.srmecotech.plantride.common.audit.AuditLogRepository;
import com.srmecotech.plantride.common.audit.AuditService;
import com.srmecotech.plantride.common.config.AppProperties;
import com.srmecotech.plantride.common.error.BusinessRuleException;
import com.srmecotech.plantride.common.error.ForbiddenOperationException;
import com.srmecotech.plantride.common.error.InvalidRequestException;
import com.srmecotech.plantride.common.error.NotFoundException;
import com.srmecotech.plantride.common.security.AuthenticatedUser;
import com.srmecotech.plantride.desk.dto.DeskDtos.AuditLogDto;
import com.srmecotech.plantride.desk.dto.DeskDtos.ComplianceItemDto;
import com.srmecotech.plantride.desk.dto.DeskDtos.ResequenceRequest;
import com.srmecotech.plantride.desk.dto.DeskDtos.RouteStopInput;
import com.srmecotech.plantride.desk.dto.DeskDtos.UpdateRouteRulesRequest;
import com.srmecotech.plantride.masterdata.ComplianceService;
import com.srmecotech.plantride.masterdata.Driver;
import com.srmecotech.plantride.masterdata.DriverRepository;
import com.srmecotech.plantride.masterdata.Route;
import com.srmecotech.plantride.masterdata.RouteRepository;
import com.srmecotech.plantride.masterdata.RouteResolver;
import com.srmecotech.plantride.masterdata.RouteStop;
import com.srmecotech.plantride.masterdata.RouteStopRepository;
import com.srmecotech.plantride.masterdata.RouteType;
import com.srmecotech.plantride.masterdata.Stop;
import com.srmecotech.plantride.masterdata.StopRepository;
import com.srmecotech.plantride.masterdata.Vehicle;
import com.srmecotech.plantride.masterdata.VehicleRepository;
import com.srmecotech.plantride.masterdata.dto.MasterDataDtos.DocState;
import com.srmecotech.plantride.masterdata.dto.MasterDataDtos.DocumentStatus;
import com.srmecotech.plantride.masterdata.dto.MasterDataDtos.RouteDto;
import com.srmecotech.plantride.matching.DispatchRequestedEvent;
import com.srmecotech.plantride.safety.DocumentExpiryMonitor;
import com.srmecotech.plantride.trip.TripRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Fleet admin: route rules and sequence (no release needed), compliance, audit trail. */
@Service
public class AdminService {

    private final RouteRepository routeRepository;
    private final RouteStopRepository routeStopRepository;
    private final StopRepository stopRepository;
    private final RouteResolver routeResolver;
    private final BookingRepository bookingRepository;
    private final TripRepository tripRepository;
    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final ComplianceService complianceService;
    private final AuditService auditService;
    private final AuditLogRepository auditLogRepository;
    private final DocumentExpiryMonitor documentExpiryMonitor;
    private final ApplicationEventPublisher events;
    private final DataSource dataSource;
    private final AppProperties properties;
    private final Clock clock;

    public AdminService(RouteRepository routeRepository, RouteStopRepository routeStopRepository,
                        StopRepository stopRepository, RouteResolver routeResolver, BookingRepository bookingRepository,
                        TripRepository tripRepository, VehicleRepository vehicleRepository,
                        DriverRepository driverRepository, ComplianceService complianceService,
                        AuditService auditService, AuditLogRepository auditLogRepository,
                        DocumentExpiryMonitor documentExpiryMonitor, ApplicationEventPublisher events,
                        DataSource dataSource, AppProperties properties, Clock clock) {
        this.routeRepository = routeRepository;
        this.routeStopRepository = routeStopRepository;
        this.stopRepository = stopRepository;
        this.routeResolver = routeResolver;
        this.bookingRepository = bookingRepository;
        this.tripRepository = tripRepository;
        this.vehicleRepository = vehicleRepository;
        this.driverRepository = driverRepository;
        this.complianceService = complianceService;
        this.auditService = auditService;
        this.auditLogRepository = auditLogRepository;
        this.documentExpiryMonitor = documentExpiryMonitor;
        this.events = events;
        this.dataSource = dataSource;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<RouteDto> routes() {
        return routeRepository.findAllWithStops().stream()
                .map(r -> RouteDto.of(com.srmecotech.plantride.masterdata.RoutePlan.of(r)))
                .toList();
    }

    @Transactional
    public RouteDto updateRules(Long routeId, UpdateRouteRulesRequest request, AuthenticatedUser actor) {
        Route route = routeRepository.findById(routeId).orElseThrow(() -> new NotFoundException("Route", routeId));
        checkVersion(route, request.version());
        List<String> changes = new ArrayList<>();
        change(changes, "max passengers", route.getMaxPassengers(), request.maxPassengers());
        change(changes, "max wait (min)", route.getMaxWaitMin(), request.maxWaitMin());
        change(changes, "max detour (min)", route.getMaxDetourMin(), request.maxDetourMin());
        change(changes, "speed limit (km/h)", route.getSpeedLimitKmh(), request.speedLimitKmh());
        change(changes, "shuttle frequency (min)", route.getFrequencyMin(), request.frequencyMin());
        if (changes.isEmpty()) {
            return RouteDto.of(routeResolver.planFor(routeId));
        }
        route.setMaxPassengers(request.maxPassengers());
        route.setMaxWaitMin(request.maxWaitMin());
        route.setMaxDetourMin(request.maxDetourMin());
        route.setSpeedLimitKmh(request.speedLimitKmh());
        route.setFrequencyMin(request.frequencyMin());
        route.setUpdatedAt(LocalDateTime.now(clock));
        routeRepository.saveAndFlush(route);
        auditService.record(actor, "ROUTE_RULES_UPDATED", "ROUTE", routeId, route.label() + ": " + String.join("; ", changes));
        // A higher cap or longer detour may let waiting riders in right away.
        events.publishEvent(new DispatchRequestedEvent("rules changed on " + route.getCode()));
        return RouteDto.of(routeResolver.planFor(routeId));
    }

    /**
     * Replaces a route's stop sequence. Refused while the route has live
     * bookings or trips, because they refer to positions in the old sequence.
     */
    @Transactional
    public RouteDto resequence(Long routeId, ResequenceRequest request, AuthenticatedUser actor) {
        Route route = routeRepository.findWithStops(routeId).orElseThrow(() -> new NotFoundException("Route", routeId));
        checkVersion(route, request.version());
        if (bookingRepository.hasActiveOnRoute(routeId)
                || tripRepository.findOpenTrips().stream().anyMatch(t -> t.getRoute().getId().equals(routeId))) {
            throw new BusinessRuleException("ROUTE_IN_USE", route.label()
                    + " has rides booked or running. Change the sequence when the route is idle.");
        }
        List<RouteStopInput> input = request.stops();
        Map<Long, Stop> stops = stopRepository.findAllById(input.stream().map(RouteStopInput::stopId).distinct().toList())
                .stream().collect(Collectors.toMap(Stop::getId, Function.identity()));
        for (int i = 0; i < input.size(); i++) {
            Stop stop = stops.get(input.get(i).stopId());
            if (stop == null || !stop.isActive()) {
                throw new InvalidRequestException("UNKNOWN_STOP", "Stop " + input.get(i).stopId() + " is not in service.");
            }
            if (i > 0 && input.get(i).stopId().equals(input.get(i - 1).stopId())) {
                throw new InvalidRequestException("DUPLICATE_STOP", stop.getName() + " is listed twice in a row.");
            }
            if (i > 0 && input.get(i).legMinutes() == 0) {
                throw new InvalidRequestException("BAD_LEG", "The leg to " + stop.getName() + " needs a travel time.");
            }
        }
        if (route.getRouteType() == RouteType.LOOP && !input.get(0).stopId().equals(input.get(input.size() - 1).stopId())) {
            throw new InvalidRequestException("LOOP_NOT_CLOSED", "A loop route must end at the stop it starts from.");
        }

        String before = route.getStops().stream().map(rs -> rs.getStop().getName()).collect(Collectors.joining(" → "));
        route.setUpdatedAt(LocalDateTime.now(clock));
        routeRepository.saveAndFlush(route);

        // Delete first, in its own statement: Hibernate would otherwise insert before deleting and hit uk_route_stop_seq.
        routeStopRepository.deleteByRouteId(routeId);
        Route ref = routeRepository.getReferenceById(routeId);
        List<RouteStop> fresh = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            RouteStop rs = new RouteStop();
            rs.setRoute(ref);
            rs.setStop(stopRepository.getReferenceById(input.get(i).stopId()));
            rs.setSeq(i + 1);
            rs.setLegMinutes(i == 0 ? 0 : input.get(i).legMinutes());
            rs.setLegKm(i == 0 ? BigDecimal.ZERO : input.get(i).legKm());
            fresh.add(rs);
        }
        routeStopRepository.saveAllAndFlush(fresh);
        String after = input.stream().map(s -> stops.get(s.stopId()).getName()).collect(Collectors.joining(" → "));
        auditService.record(actor, "ROUTE_RESEQUENCED", "ROUTE", routeId, before + "  ⇒  " + after);
        return RouteDto.of(routeResolver.planFor(routeId));
    }

    /** Every document that is expiring or expired, soonest first. */
    @Transactional(readOnly = true)
    public List<ComplianceItemDto> compliance() {
        List<ComplianceItemDto> items = new ArrayList<>();
        for (Vehicle v : vehicleRepository.findAllForBoard()) {
            for (DocumentStatus doc : complianceService.vehicleDocuments(v)) {
                if (doc.state() != DocState.VALID) {
                    items.add(new ComplianceItemDto("VEHICLE", v.getId(), v.getCode(),
                            v.getRegistrationNo() + (v.getVendor() == null ? "" : " · " + v.getVendor().getName()),
                            doc.document(), doc.reference(), doc.expiresOn(), doc.daysLeft(), doc.state(), doc.label(),
                            doc.state() == DocState.EXPIRED));
                }
            }
        }
        for (Driver d : driverRepository.findAllWithUser()) {
            for (DocumentStatus doc : complianceService.driverDocuments(d)) {
                if (doc.state() != DocState.VALID) {
                    items.add(new ComplianceItemDto("DRIVER", d.getId(), d.getDriverCode(), d.getUser().getFullName(),
                            doc.document(), doc.reference(), doc.expiresOn(), doc.daysLeft(), doc.state(), doc.label(),
                            doc.state() == DocState.EXPIRED));
                }
            }
        }
        items.sort(Comparator.comparingLong(ComplianceItemDto::daysLeft));
        return items;
    }

    @Transactional(readOnly = true)
    public List<AuditLogDto> audit(int limit) {
        return auditLogRepository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, Math.min(Math.max(limit, 1), 200)))
                .stream()
                .map(a -> new AuditLogDto(a.getId(), a.getActorName(), a.getAction(), a.getEntityType(), a.getEntityId(),
                        a.getDetails(), a.getCreatedAt()))
                .toList();
    }

    /**
     * Rehearsal helper: reloads schema.sql and data.sql so the next demo run
     * starts from the known state. Disabled unless plantride.demo.reset-enabled.
     */
    public String resetDemo(AuthenticatedUser actor) {
        if (!properties.demo().resetEnabled()) {
            throw new ForbiddenOperationException("Demo reset is disabled in this environment.");
        }
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("schema.sql"), new ClassPathResource("data.sql"));
        populator.setSqlScriptEncoding("UTF-8");
        populator.execute(dataSource);
        documentExpiryMonitor.scan();
        documentExpiryMonitor.recordReset(actor);
        return "Demo data reloaded. Every account, booking and trip is back to its starting state.";
    }

    private static void checkVersion(Route route, Long version) {
        if (version == null || route.getVersion() != version) {
            throw new BusinessRuleException("STALE_DATA", route.label() + " was changed by someone else. Refresh and try again.");
        }
    }

    private static void change(List<String> changes, String label, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            changes.add(label + " " + (before == null ? "none" : before) + " → " + (after == null ? "none" : after));
        }
    }
}
