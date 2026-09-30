package com.srmecotech.plantride.support;

import com.srmecotech.plantride.booking.Booking;
import com.srmecotech.plantride.booking.BookingStatus;
import com.srmecotech.plantride.booking.RideType;
import com.srmecotech.plantride.common.config.AppProperties;
import com.srmecotech.plantride.identity.AppUser;
import com.srmecotech.plantride.identity.Role;
import com.srmecotech.plantride.masterdata.CostCentre;
import com.srmecotech.plantride.masterdata.Driver;
import com.srmecotech.plantride.masterdata.Route;
import com.srmecotech.plantride.masterdata.RoutePlan;
import com.srmecotech.plantride.masterdata.RouteStop;
import com.srmecotech.plantride.masterdata.RouteType;
import com.srmecotech.plantride.masterdata.Stop;
import com.srmecotech.plantride.masterdata.Vehicle;
import com.srmecotech.plantride.masterdata.VehicleStatus;
import com.srmecotech.plantride.masterdata.VehicleType;
import com.srmecotech.plantride.masterdata.Vendor;
import com.srmecotech.plantride.trip.Duty;
import com.srmecotech.plantride.trip.DutyStatus;
import com.srmecotech.plantride.trip.Trip;
import com.srmecotech.plantride.trip.TripStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** In-memory domain objects for unit tests: Route 1 of the deck (Gate 2 loop) and friends. */
public final class Fixtures {

    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    /** 22 Sep 2026, 14:05 IST: the golden-path booking time in the deck. */
    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-22T08:35:00Z"), IST);
    public static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    public static final LocalDate TODAY = NOW.toLocalDate();

    private Fixtures() {
    }

    public static AppProperties properties() {
        return new AppProperties("Asia/Kolkata", "http://localhost:8080",
                new AppProperties.Security("unit-test-secret-unit-test-secret-unit-test-secret", 12),
                new AppProperties.Cors(List.of("*")),
                new AppProperties.Dispatch(false, 15000),
                new AppProperties.Matching(18, 1.35, 1, 1.0, 0.5, 2.0, 1.5, 1.0),
                new AppProperties.Demo(false));
    }

    public static Stop stop(long id, String name, double lat, double lng) {
        Stop s = new Stop();
        s.setId(id);
        s.setCode(name.toUpperCase().replace(' ', '_'));
        s.setName(name);
        s.setZone("Z1");
        s.setLatitude(lat);
        s.setLongitude(lng);
        s.setGeofenceM(50);
        return s;
    }

    public static final Stop GATE2 = stop(1, "Gate 2", 18.6212, 73.7985);
    public static final Stop ADMIN = stop(2, "Admin Block", 18.6268, 73.8021);
    public static final Stop COKE = stop(3, "Coke Plant", 18.6335, 73.8090);
    public static final Stop BF = stop(4, "Blast Furnace", 18.6301, 73.8171);
    public static final Stop TOWNSHIP = stop(9, "Township", 18.5985, 73.7850);

    /** Route 1 · North corridor: Gate 2 -> Admin (4 min, 0.9) -> Coke (6, 1.4) -> BF (5, 1.1) -> Gate 2 (9, 2.1). */
    public static RoutePlan route1(int cap) {
        Route r = new Route();
        r.setId(1L);
        r.setCode("R1");
        r.setName("North corridor");
        r.setRouteType(RouteType.LOOP);
        r.setFirstDeparture(LocalTime.of(6, 0));
        r.setFrequencyMin(20);
        r.setMaxPassengers(cap);
        r.setMaxWaitMin(5);
        r.setMaxDetourMin(7);
        r.setSpeedLimitKmh(20);
        List<RouteStop> stops = new ArrayList<>();
        stops.add(routeStop(r, GATE2, 1, 0, "0.00"));
        stops.add(routeStop(r, ADMIN, 2, 4, "0.90"));
        stops.add(routeStop(r, COKE, 3, 6, "1.40"));
        stops.add(routeStop(r, BF, 4, 5, "1.10"));
        stops.add(routeStop(r, GATE2, 5, 9, "2.10"));
        r.setStops(stops);
        return RoutePlan.of(r);
    }

    public static Route otherRoute() {
        Route r = new Route();
        r.setId(2L);
        r.setCode("R2");
        r.setName("Mill corridor");
        r.setMaxPassengers(3);
        return r;
    }

    private static RouteStop routeStop(Route r, Stop s, int seq, int minutes, String km) {
        RouteStop rs = new RouteStop();
        rs.setRoute(r);
        rs.setStop(s);
        rs.setSeq(seq);
        rs.setLegMinutes(minutes);
        rs.setLegKm(new BigDecimal(km));
        return rs;
    }

    public static Vehicle cab(long id, String code, Stop at) {
        Vehicle v = new Vehicle();
        v.setId(id);
        v.setCode(code);
        v.setRegistrationNo("MH 12 XX " + id);
        v.setVehicleType(VehicleType.CAB);
        v.setSeatCapacity(4);
        v.setCostPerKm(new BigDecimal("18.00"));
        v.setInsurancePolicyNo("POL-" + id);
        v.setInsuranceExpiry(TODAY.plusDays(200));
        v.setFitnessExpiry(TODAY.plusDays(200));
        v.setPucExpiry(TODAY.plusDays(200));
        v.setStatus(VehicleStatus.ACTIVE);
        v.setCurrentStop(at);
        v.setLastLatitude(at.getLatitude());
        v.setLastLongitude(at.getLongitude());
        return v;
    }

    public static Vehicle vendorCab(long id, String code, Stop at) {
        Vehicle v = cab(id, code, at);
        Vendor vendor = new Vendor();
        vendor.setId(1L);
        vendor.setName("Vendor A");
        v.setVendor(vendor);
        return v;
    }

    public static Driver driver(long id, String name) {
        AppUser u = new AppUser();
        u.setId(100 + id);
        u.setFullName(name);
        u.setRole(Role.DRIVER);
        u.setPhone("+91 98000 000" + id);
        Driver d = new Driver();
        d.setId(id);
        d.setUser(u);
        d.setDriverCode("DRV-" + id);
        d.setLicenceNo("LIC-" + id);
        d.setLicenceExpiry(TODAY.plusDays(500));
        d.setGatePassNo("GP-" + id);
        d.setGatePassExpiry(TODAY.plusDays(100));
        d.setGatesAllowed("All gates");
        d.setMaxDutyMinutes(540);
        return d;
    }

    public static Duty duty(long id, Driver driver, Vehicle vehicle, int minutesOnDuty) {
        Duty d = new Duty();
        d.setId(id);
        d.setDriver(driver);
        d.setVehicle(vehicle);
        d.setStatus(DutyStatus.ACTIVE);
        d.setStartedAt(NOW.minusMinutes(minutesOnDuty));
        d.setStartOdometer(1000);
        d.setChecklist("TYRES,LIGHTS,BELTS,FUEL,FIRST_AID");
        return d;
    }

    public static Trip trip(long id, Route route, Vehicle vehicle, Driver driver, int startSeq, Integer currentSeq) {
        Trip t = new Trip();
        t.setId(id);
        t.setTripCode("T-" + id);
        t.setRoute(route);
        t.setVehicle(vehicle);
        t.setDriver(driver);
        t.setStatus(currentSeq == null ? TripStatus.PLANNED : TripStatus.IN_PROGRESS);
        t.setStartSeq(startSeq);
        t.setCurrentSeq(currentSeq);
        t.setLastArrivedAt(currentSeq == null ? null : NOW);
        t.setCreatedAt(NOW);
        return t;
    }

    public static Booking rider(long id, Trip trip, int fromSeq, int toSeq, BookingStatus status) {
        Booking b = new Booking();
        b.setId(id);
        b.setBookingCode("PR-" + id);
        b.setTrip(trip);
        b.setFromSeq(fromSeq);
        b.setToSeq(toSeq);
        b.setSeats(1);
        b.setStatus(status);
        b.setRideType(RideType.SHARED);
        b.setRiderName("Rider " + id);
        return b;
    }

    public static CostCentre costCentre(String code) {
        CostCentre cc = new CostCentre();
        cc.setCode(code);
        cc.setName(code + " dept");
        cc.setMonthlyBudget(new BigDecimal("1000"));
        return cc;
    }
}
