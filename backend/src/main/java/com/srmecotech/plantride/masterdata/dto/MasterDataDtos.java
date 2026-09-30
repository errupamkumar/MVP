package com.srmecotech.plantride.masterdata.dto;

import com.srmecotech.plantride.masterdata.Route;
import com.srmecotech.plantride.masterdata.RoutePlan;
import com.srmecotech.plantride.masterdata.RouteStop;
import com.srmecotech.plantride.masterdata.RouteType;
import com.srmecotech.plantride.masterdata.Stop;
import com.srmecotech.plantride.masterdata.Vehicle;
import com.srmecotech.plantride.masterdata.VehicleType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class MasterDataDtos {

    private MasterDataDtos() {
    }

    public record StopRef(Long id, String code, String name) {
        public static StopRef of(Stop s) {
            return s == null ? null : new StopRef(s.getId(), s.getCode(), s.getName());
        }
    }

    public record RouteRef(Long id, String code, String name, String label) {
        public static RouteRef of(Route r) {
            return r == null ? null : new RouteRef(r.getId(), r.getCode(), r.getName(), r.label());
        }
    }

    public record VehicleRef(Long id, String code, String registrationNo, VehicleType vehicleType, int seatCapacity) {
        public static VehicleRef of(Vehicle v) {
            return v == null ? null : new VehicleRef(v.getId(), v.getCode(), v.getRegistrationNo(), v.getVehicleType(),
                    v.getSeatCapacity());
        }
    }

    public record StopDto(Long id, String code, String name, String zone, double latitude, double longitude,
                          int geofenceM) {
        public static StopDto of(Stop s) {
            return new StopDto(s.getId(), s.getCode(), s.getName(), s.getZone(), s.getLatitude(), s.getLongitude(),
                    s.getGeofenceM());
        }
    }

    public record RouteStopDto(int seq, Long stopId, String stopCode, String stopName, int legMinutes,
                               BigDecimal legKm, int geofenceM) {
        public static RouteStopDto of(RouteStop rs) {
            return new RouteStopDto(rs.getSeq(), rs.getStop().getId(), rs.getStop().getCode(), rs.getStop().getName(),
                    rs.getLegMinutes(), rs.getLegKm(), rs.getStop().getGeofenceM());
        }
    }

    public record RouteDto(
            Long id,
            String code,
            String name,
            String label,
            RouteType routeType,
            String serviceNote,
            Integer frequencyMin,
            int maxPassengers,
            int maxWaitMin,
            int maxDetourMin,
            int speedLimitKmh,
            boolean active,
            long version,
            LocalDateTime updatedAt,
            int totalMinutes,
            BigDecimal totalKm,
            List<RouteStopDto> stops) {

        public static RouteDto of(RoutePlan plan) {
            Route r = plan.route();
            return new RouteDto(r.getId(), r.getCode(), r.getName(), r.label(), r.getRouteType(), r.getServiceNote(),
                    r.getFrequencyMin(), r.getMaxPassengers(), r.getMaxWaitMin(), r.getMaxDetourMin(),
                    r.getSpeedLimitKmh(), r.isActive(), r.getVersion(), r.getUpdatedAt(), plan.totalMinutes(),
                    plan.totalKm(), plan.stops().stream().map(RouteStopDto::of).toList());
        }
    }

    /** A stop the rider can reach from the chosen pickup, via the fastest configured route. */
    public record DestinationDto(Long stopId, String stopCode, String stopName, Long routeId, String routeCode,
                                 String routeName, int rideMinutes, BigDecimal rideKm) {
    }

    public enum DocState {
        VALID,
        /** Inside the alert window (30 days by default): alerts go out, dispatch continues. */
        EXPIRING,
        /** Past expiry: dispatch and duty are blocked. */
        EXPIRED
    }

    public record DocumentStatus(String document, String reference, LocalDate expiresOn, long daysLeft,
                                 DocState state, String label) {
    }
}
