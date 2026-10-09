package com.srmecotech.plantride.booking;

import com.srmecotech.plantride.booking.dto.RiderDtos.ShuttleInfoDto;
import com.srmecotech.plantride.masterdata.Route;
import com.srmecotech.plantride.masterdata.RoutePlan;
import com.srmecotech.plantride.masterdata.RouteResolver;
import com.srmecotech.plantride.masterdata.Stop;
import com.srmecotech.plantride.masterdata.Vehicle;
import com.srmecotech.plantride.trip.Duty;
import com.srmecotech.plantride.trip.DutyRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * The shuttle's timetable: departures leave the first stop every frequency
 * minutes from first_departure and reach later stops after the configured
 * leg times. The shuttle is never dispatched, so this is information only.
 */
@Component
public class ShuttleTimetable {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final DutyRepository dutyRepository;
    private final RouteResolver routeResolver;

    public ShuttleTimetable(DutyRepository dutyRepository, RouteResolver routeResolver) {
        this.dutyRepository = dutyRepository;
        this.routeResolver = routeResolver;
    }

    /** The next shuttle at a stop on any route, or null when no shuttle serving it is on duty. */
    @Transactional(readOnly = true)
    public ShuttleInfoDto nextAt(Long stopId, LocalDateTime now) {
        for (RoutePlan plan : routeResolver.activePlans()) {
            List<Integer> seqs = plan.seqsOfStop(stopId);
            if (seqs.isEmpty()) {
                continue;
            }
            Optional<ShuttleInfoDto> info = forStop(plan, seqs.get(0), now);
            if (info.isPresent()) {
                return info.get();
            }
        }
        return null;
    }

    /** The next shuttle at route position {@code fromSeq}, if the route runs a shuttle that is on duty. */
    @Transactional(readOnly = true)
    public Optional<ShuttleInfoDto> forStop(RoutePlan plan, int fromSeq, LocalDateTime now) {
        Route route = plan.route();
        if (route.getFrequencyMin() == null || route.getFrequencyMin() <= 0) {
            return Optional.empty();
        }
        Optional<Duty> shuttleDuty = dutyRepository.findActiveWithVehicleAndDriver().stream()
                .filter(d -> d.getVehicle().isShuttle())
                .filter(d -> d.getVehicle().getHomeRoute() != null && d.getVehicle().getHomeRoute().getId().equals(route.getId()))
                .findFirst();
        if (shuttleDuty.isEmpty()) {
            return Optional.empty();
        }
        int frequency = route.getFrequencyMin();
        int offset = plan.minutesBetween(plan.stops().get(0).getSeq(), fromSeq);
        LocalDateTime firstAtStop = now.toLocalDate().atTime(route.getFirstDeparture()).plusMinutes(offset);
        long sinceFirst = Duration.between(firstAtStop, now).toMinutes();
        boolean beforeService = sinceFirst < 0;
        int wait = beforeService ? (int) -sinceFirst : (int) ((frequency - (sinceFirst % frequency)) % frequency);
        Vehicle shuttle = shuttleDuty.get().getVehicle();
        Stop stop = plan.stopAt(fromSeq);
        return Optional.of(new ShuttleInfoDto(shuttle.getCode(), "Route " + route.getCode().replace("R", ""),
                stop.getId(), stop.getName(), wait, frequency, shuttle.getSeatCapacity(),
                beforeService ? firstAtStop.toLocalTime().format(HH_MM) : null));
    }
}
