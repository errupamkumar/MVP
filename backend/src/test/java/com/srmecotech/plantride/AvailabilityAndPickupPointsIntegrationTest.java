package com.srmecotech.plantride;

import com.fasterxml.jackson.databind.JsonNode;
import com.srmecotech.plantride.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * When no cab is free the rider still learns when a ride can come, which cabs
 * are near (driver, seats, distance), and nearby pickup points; a waiting
 * request shows its search progress and can be searched again. Pickup points
 * are managed by the admin.
 */
class AvailabilityAndPickupPointsIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("A free seat is offered now, with the cab's driver, seats and distance")
    void cabAvailableNow() throws Exception {
        signOnC12();
        JsonNode a = availability(login("LHS-40218"), "Gate 2", "Blast Furnace");

        assertThat(a.get("availableNow").asBoolean()).isTrue();
        assertThat(a.at("/nextAtPickup/vehicleCode").asText()).isEqualTo("C-12");
        assertThat(a.at("/nextAtPickup/availableNow").asBoolean()).isTrue();
        assertThat(a.get("summary").asText()).startsWith("C-12 can pick you up at Gate 2");
        assertThat(a.at("/pickup/landmark").asText()).isEqualTo("Security cabin, visitor lane");

        JsonNode c12 = cab(a, "C-12");
        assertThat(c12.get("canTakeYou").asBoolean()).isTrue();
        assertThat(c12.get("driverName").asText()).isEqualTo("S. Kumar");
        assertThat(c12.get("driverPhone").asText()).startsWith("+91");
        assertThat(c12.get("seatsTaken").asInt()).isEqualTo(2);
        assertThat(c12.get("capacity").asInt()).isEqualTo(3);
        assertThat(c12.get("distanceKm").asDouble()).isLessThan(0.1);
        assertThat(c12.get("etaMinutes").isNull()).isFalse();
        // Nearest first: C-12 is parked at Gate 2.
        assertThat(a.at("/nearbyCabs/0/vehicleCode").asText()).isEqualTo("C-12");
    }

    @Test
    @DisplayName("No cab free: the next ride at the pickup, and when each busy cab frees up")
    void noCabFreeShowsWhenOneComes() throws Exception {
        signOnC12();
        mvc.perform(authPost(login("LHS-40218"), "/api/rider/bookings", bookingBody("Gate 2", "Blast Furnace", "SHARED", 1, "A. Raghavan")))
                .andExpect(status().isCreated());

        JsonNode a = availability(login("LHS-44190"), "Gate 2", "Admin Block");

        assertThat(a.get("availableNow").asBoolean()).isFalse();
        assertThat(a.get("summary").asText()).startsWith("No cab is free right now");
        assertThat(a.get("vehiclesOnDuty").asInt()).isEqualTo(6);

        JsonNode c12Next = StreamSupport.stream(a.get("upcomingAtPickup").spliterator(), false)
                .filter(r -> "C-12".equals(r.get("vehicleCode").asText()))
                .findFirst().orElseThrow();
        assertThat(c12Next.get("availableNow").asBoolean()).isFalse();
        assertThat(c12Next.get("inMinutes").asInt()).isPositive();
        assertThat(c12Next.get("note").asText()).contains("last drop at Blast Furnace");
        assertThat(a.at("/nextAtPickup/inMinutes").asInt()).isLessThanOrEqualTo(c12Next.get("inMinutes").asInt());

        JsonNode c12 = cab(a, "C-12");
        assertThat(c12.get("status").asText()).isEqualTo("FULL");
        assertThat(c12.get("seatsTaken").asInt()).isEqualTo(3);
        assertThat(c12.get("canTakeYou").asBoolean()).isFalse();
        assertThat(c12.get("availableInMinutes").asInt()).isEqualTo(c12Next.get("inMinutes").asInt());

        JsonNode c44 = cab(a, "C-44");
        assertThat(c44.get("status").asText()).isEqualTo("OFF_ROAD");
        assertThat(c44.get("availableInMinutes").isNull()).isTrue();

        JsonNode c21 = cab(a, "C-21");
        assertThat(c21.get("canTakeYou").asBoolean()).isFalse();
        assertThat(c21.get("note").asText()).contains("duty-hour limit");

        JsonNode shuttle = cab(a, "S-03");
        assertThat(shuttle.get("status").asText()).isEqualTo("SHUTTLE");
        assertThat(shuttle.get("note").asText()).contains("walk up");
    }

    @Test
    @DisplayName("Nearby pickup points on a route to the same drop are suggested, within the admin's radius")
    void nearbyPickupPoints() throws Exception {
        signOnC12();
        String raghavan = login("LHS-40218");
        JsonNode a = availability(raghavan, "Gate 2", "Blast Furnace");
        JsonNode admin = StreamSupport.stream(a.get("alternativeSpots").spliterator(), false)
                .filter(s -> "Admin Block".equals(s.at("/stop/name").asText()))
                .findFirst().orElseThrow();
        assertThat(admin.get("walkMetres").asInt()).isBetween(700, 1200);
        assertThat(admin.get("walkMinutes").asInt()).isPositive();
        assertThat(admin.at("/route/label").asText()).isEqualTo("Route 1 · North corridor");
        assertThat(admin.at("/stop/landmark").asText()).isEqualTo("Main porch, facing the flag post");
        // C-12 is at Gate 2 already, so walking to Admin Block is not sooner.
        assertThat(admin.get("sooner").asBoolean()).isFalse();

        // The drop itself is never suggested as a pickup.
        JsonNode toAdmin = availability(login("LHS-44190"), "Gate 2", "Admin Block");
        assertThat(toAdmin.get("alternativeSpots").findValuesAsText("name")).doesNotContain("Admin Block");

        jdbc.update("UPDATE app_setting SET setting_value = '100' WHERE setting_key = 'NEARBY_SPOT_RADIUS_M'");
        assertThat(availability(raghavan, "Gate 2", "Blast Furnace").get("alternativeSpots")).isEmpty();
    }

    @Test
    @DisplayName("A waiting request shows search progress, and Search again matches it as soon as a seat exists")
    void waitingRequestSearchAgain() throws Exception {
        signOnC12();
        mvc.perform(authPost(login("LHS-40218"), "/api/rider/bookings", bookingBody("Gate 2", "Blast Furnace", "SHARED", 1, "A. Raghavan")))
                .andExpect(status().isCreated());
        String gupta = login("LHS-44190");
        JsonNode booked = read(mvc.perform(authPost(gupta, "/api/rider/bookings", bookingBody("Gate 2", "Admin Block", "SHARED", 1, "N. Gupta")))
                .andExpect(status().isCreated()).andReturn());
        assertThat(booked.get("status").asText()).isEqualTo("REQUESTED");
        long id = booked.get("id").asLong();

        clock.advance(Duration.ofSeconds(40));
        JsonNode a = read(mvc.perform(authGet(gupta, "/api/rider/bookings/{id}/availability", id)).andExpect(status().isOk()).andReturn());
        JsonNode search = a.get("search");
        // MySQL DATETIME rounds the stored times to the second, hence the one-second tolerance.
        assertThat(search.get("searchingForSeconds").asLong()).isBetween(39L, 40L);
        assertThat(search.get("lastCheckedSecondsAgo").asLong()).isBetween(39L, 40L);
        assertThat(search.get("checkEverySeconds").asLong()).isEqualTo(15);
        assertThat(search.get("nextCheckInSeconds").asLong()).isBetween(0L, 15L);
        assertThat(search.get("expiresInSeconds").asLong()).isBetween(90L * 60 - 41, 90L * 60 - 39);
        assertThat(search.get("lastResult").asText()).contains("3-rider cap");
        // C-12 still carries its three riders; the waiting request is not one of them.
        assertThat(cab(a, "C-12").get("seatsTaken").asInt()).isEqualTo(3);

        // A fourth seat appears without anything triggering dispatch (the sweep is off in tests).
        jdbc.update("UPDATE route SET max_passengers = 4 WHERE code = 'R1'");
        mvc.perform(authPost(gupta, "/api/rider/bookings/{id}/search", null, id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.vehicle.code").value("C-12"));

        // Searching a booking that is no longer waiting just returns it.
        mvc.perform(authPost(gupta, "/api/rider/bookings/{id}/search", null, id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"));
        // Another rider's booking is not visible.
        mvc.perform(authPost(login("LHS-40218"), "/api/rider/bookings/{id}/search", null, id))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Search again right after a check is a no-op, so repeated taps cannot hammer the engine")
    void searchAgainIsThrottled() throws Exception {
        signOnC12();
        mvc.perform(authPost(login("LHS-40218"), "/api/rider/bookings", bookingBody("Gate 2", "Blast Furnace", "SHARED", 1, "A. Raghavan")))
                .andExpect(status().isCreated());
        String gupta = login("LHS-44190");
        long id = read(mvc.perform(authPost(gupta, "/api/rider/bookings", bookingBody("Gate 2", "Admin Block", "SHARED", 1, "N. Gupta")))
                .andReturn()).get("id").asLong();
        jdbc.update("UPDATE route SET max_passengers = 4 WHERE code = 'R1'");

        mvc.perform(authPost(gupta, "/api/rider/bookings/{id}/search", null, id))
                .andExpect(jsonPath("$.status").value("REQUESTED"));
        clock.advance(Duration.ofSeconds(5));
        mvc.perform(authPost(gupta, "/api/rider/bookings/{id}/search", null, id))
                .andExpect(jsonPath("$.status").value("ASSIGNED"));
    }

    @Test
    @DisplayName("The admin adds, edits and retires pickup points; riders see them; every change is audited")
    void adminManagesPickupPoints() throws Exception {
        String admin = login("admin");
        JsonNode points = read(mvc.perform(authGet(admin, "/api/admin/stops")).andExpect(status().isOk()).andReturn());
        assertThat(points).hasSize(13);
        JsonNode gate2 = StreamSupport.stream(points.spliterator(), false)
                .filter(p -> "Gate 2".equals(p.get("name").asText())).findFirst().orElseThrow();
        assertThat(gate2.get("routes").get(0).asText()).isEqualTo("Route 1 · North corridor");
        assertThat(gate2.get("liveBookings").asLong()).isEqualTo(3); // M. Iyer and S. Banerjee waiting, A. Raghavan scheduled

        Map<String, Object> gate3 = point("gate3", "Gate 3", "z1", "Security cabin, truck lane", 18.6195, 73.7955, 40, null);
        JsonNode created = read(mvc.perform(authPost(admin, "/api/admin/stops", gate3))
                .andExpect(status().isCreated()).andReturn());
        assertThat(created.get("code").asText()).isEqualTo("GATE3");
        assertThat(created.get("zone").asText()).isEqualTo("Z1");
        assertThat(created.get("active").asBoolean()).isTrue();
        assertThat(created.get("routes")).isEmpty();
        long gate3Id = created.get("id").asLong();

        // Riders see the new point and where to wait.
        JsonNode stops = read(mvc.perform(authGet(login("LHS-40218"), "/api/stops")).andReturn());
        assertThat(stops.findValuesAsText("landmark")).contains("Security cabin, truck lane");

        mvc.perform(authPost(admin, "/api/admin/stops", point("GATE3", "Gate 3 East", "Z1", null, 18.62, 73.79, 40, null)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_CODE"));
        mvc.perform(authPost(admin, "/api/admin/stops", point("GATE3B", "gate 2", "Z1", null, 18.62, 73.79, 40, null)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_NAME"));
        mvc.perform(authPost(admin, "/api/admin/stops", point("DELHI", "Delhi office", "Z9", null, 28.61, 77.21, 40, null)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("FAR_FROM_PLANT"));
        mvc.perform(authPost(admin, "/api/admin/stops", point("X", "", "Z1", null, 18.62, 73.79, 5, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field").value(org.hamcrest.Matchers.hasItems("code", "name", "geofenceM")));

        // Edit: landmark and radius.
        mvc.perform(authPut(admin, "/api/admin/stops/{id}",
                        point(null, "Gate 3", "Z1", "Truck lane, near the weighbridge", 18.6195, 73.7955, 60, true), gate3Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.landmark").value("Truck lane, near the weighbridge"))
                .andExpect(jsonPath("$.geofenceM").value(60));

        // A point on a route, or with open bookings, cannot be retired.
        mvc.perform(authPut(admin, "/api/admin/stops/{id}", point(null, "Gate 2", "Z1", "Security cabin, visitor lane",
                        18.6212, 73.7985, 40, false), stopId("Gate 2")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STOP_ON_ROUTE"))
                .andExpect(jsonPath("$.message").value(containsString("Route 1 · North corridor")));

        // An unused point can; it disappears for riders and from suggestions.
        mvc.perform(authPut(admin, "/api/admin/stops/{id}",
                        point(null, "Gate 3", "Z1", "Truck lane, near the weighbridge", 18.6195, 73.7955, 60, false), gate3Id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        stops = read(mvc.perform(authGet(login("LHS-40218"), "/api/stops")).andReturn());
        assertThat(stops.findValuesAsText("name")).doesNotContain("Gate 3");

        List<String> audit = jdbc.queryForList("SELECT action FROM audit_log WHERE entity_type = 'STOP' ORDER BY id", String.class);
        assertThat(audit).containsExactly("STOP_CREATED", "STOP_UPDATED", "STOP_UPDATED");
        String retired = jdbc.queryForObject("SELECT details FROM audit_log WHERE entity_type = 'STOP' ORDER BY id DESC LIMIT 1", String.class);
        assertThat(retired).contains("in service true → false");

        // Desk operators and riders cannot manage pickup points.
        mvc.perform(authGet(login("desk"), "/api/admin/stops")).andExpect(status().isForbidden());
        mvc.perform(authPost(login("LHS-40218"), "/api/admin/stops", gate3)).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ helpers

    private void signOnC12() throws Exception {
        mvc.perform(authPost(login("DRV-2281"), "/api/driver/duty/start", startDutyBody("C-12"))).andExpect(status().isOk());
    }

    private JsonNode availability(String token, String from, String to) throws Exception {
        return read(mvc.perform(authPost(token, "/api/rider/availability",
                        Map.of("fromStopId", stopId(from), "toStopId", stopId(to), "seats", 1)))
                .andExpect(status().isOk()).andReturn());
    }

    private static JsonNode cab(JsonNode availability, String code) {
        return StreamSupport.stream(availability.get("nearbyCabs").spliterator(), false)
                .filter(c -> code.equals(c.get("vehicleCode").asText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(code + " not in nearbyCabs: " + availability.get("nearbyCabs")));
    }

    private static Map<String, Object> point(String code, String name, String zone, String landmark,
                                             double lat, double lng, int geofence, Boolean active) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code == null ? null : code.toUpperCase());
        body.put("name", name);
        body.put("zone", zone);
        body.put("landmark", landmark);
        body.put("latitude", lat);
        body.put("longitude", lng);
        body.put("geofenceM", geofence);
        body.put("active", active);
        return body;
    }
}
