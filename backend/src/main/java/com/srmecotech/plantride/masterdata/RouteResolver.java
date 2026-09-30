package com.srmecotech.plantride.masterdata;

import com.srmecotech.plantride.common.error.InvalidRequestException;
import com.srmecotech.plantride.common.error.NotFoundException;
import com.srmecotech.plantride.masterdata.dto.MasterDataDtos.DestinationDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns "from stop A to stop B" into a position range on a configured
 * route. Vehicles only run a route forwards, so B must come after A in the
 * sequence. When several routes qualify, the shortest ride wins.
 */
@Service
public class RouteResolver {

    private final RouteRepository routeRepository;
    private final StopRepository stopRepository;

    public RouteResolver(RouteRepository routeRepository, StopRepository stopRepository) {
        this.routeRepository = routeRepository;
        this.stopRepository = stopRepository;
    }

    @Transactional(readOnly = true)
    public List<RoutePlan> activePlans() {
        return routeRepository.findActiveWithStops().stream().map(RoutePlan::of).toList();
    }

    @Transactional(readOnly = true)
    public RoutePlan planFor(Long routeId) {
        return routeRepository.findWithStops(routeId)
                .map(RoutePlan::of)
                .orElseThrow(() -> new NotFoundException("Route", routeId));
    }

    @Transactional(readOnly = true)
    public ResolvedRide resolve(Long fromStopId, Long toStopId) {
        Stop from = stopRepository.findById(fromStopId)
                .filter(Stop::isActive)
                .orElseThrow(() -> new InvalidRequestException("UNKNOWN_STOP", "The pickup stop is not in service."));
        Stop to = stopRepository.findById(toStopId)
                .filter(Stop::isActive)
                .orElseThrow(() -> new InvalidRequestException("UNKNOWN_STOP", "The drop stop is not in service."));
        if (from.getId().equals(to.getId())) {
            throw new InvalidRequestException("SAME_STOP", "Pickup and drop cannot be the same stop.");
        }

        ResolvedRide best = null;
        for (RoutePlan plan : activePlans()) {
            for (int fromSeq : plan.seqsOfStop(from.getId())) {
                Integer toSeq = firstPositionAfter(plan, to.getId(), fromSeq);
                if (toSeq == null) {
                    continue;
                }
                ResolvedRide candidate = new ResolvedRide(plan, fromSeq, toSeq);
                if (best == null || candidate.minutes() < best.minutes()) {
                    best = candidate;
                }
            }
        }
        if (best == null) {
            throw new InvalidRequestException("NO_ROUTE",
                    "No configured route runs from " + from.getName() + " to " + to.getName() + ".");
        }
        return best;
    }

    /** Every stop reachable from {@code fromStopId}, with the shortest ride to it. */
    @Transactional(readOnly = true)
    public List<DestinationDto> destinations(Long fromStopId) {
        Map<Long, DestinationDto> byStop = new LinkedHashMap<>();
        for (RoutePlan plan : activePlans()) {
            for (int fromSeq : plan.seqsOfStop(fromStopId)) {
                for (RouteStop rs : plan.stops()) {
                    if (rs.getSeq() <= fromSeq || rs.getStop().getId().equals(fromStopId)) {
                        continue;
                    }
                    int minutes = plan.minutesBetween(fromSeq, rs.getSeq());
                    BigDecimal km = plan.kmBetween(fromSeq, rs.getSeq());
                    DestinationDto existing = byStop.get(rs.getStop().getId());
                    if (existing == null || minutes < existing.rideMinutes()) {
                        byStop.put(rs.getStop().getId(), new DestinationDto(
                                rs.getStop().getId(), rs.getStop().getCode(), rs.getStop().getName(),
                                plan.route().getId(), plan.route().getCode(), plan.route().getName(), minutes, km));
                    }
                }
            }
        }
        return byStop.values().stream()
                .sorted(Comparator.comparingInt(DestinationDto::rideMinutes).thenComparing(DestinationDto::stopName))
                .toList();
    }

    private static Integer firstPositionAfter(RoutePlan plan, Long stopId, int afterSeq) {
        return plan.stops().stream()
                .filter(s -> s.getSeq() > afterSeq && s.getStop().getId().equals(stopId))
                .map(RouteStop::getSeq)
                .findFirst()
                .orElse(null);
    }
}
