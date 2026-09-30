package com.srmecotech.plantride;

import com.fasterxml.jackson.databind.JsonNode;
import com.srmecotech.plantride.matching.DispatchService;
import com.srmecotech.plantride.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Deck 2, slide 15: what each journey does when it does not go to plan. */
class ExceptionPathsIntegrationTest extends IntegrationTestBase {

    @Autowired
    DispatchService dispatchService;

    @Test
    @DisplayName("Expired documents block duty: gate pass, vehicle insurance")
    void complianceBlocksDuty() throws Exception {
        // K. Joshi's gate pass expired 3 days ago.
        mvc.perform(authPost(login("DRV-2310"), "/api/driver/duty/start", startDutyBody("C-15")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUTY_BLOCKED"))
                .andExpect(jsonPath("$.message").value(containsString("gate pass expired")));
        // M. Ali drives C-09, whose insurance expired 2 days ago.
        mvc.perform(authPost(login("DRV-1466"), "/api/driver/duty/start", startDutyBody("C-09")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUTY_BLOCKED"))
                .andExpect(jsonPath("$.message").value(containsString("C-09 vehicle insurance expired")));
        // A vendor driver cannot take an own-fleet cab.
        mvc.perform(authPost(login("DRV-1466"), "/api/driver/duty/start", startDutyBody("C-12")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VEHICLE_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("Sign-on validates the check list and the odometer; one vehicle, one driver")
    void signOnValidation() throws Exception {
        String kumar = login("DRV-2281");
        Map<String, Object> body = new java.util.HashMap<>(startDutyBody("C-12"));
        body.put("checklist", List.of("TYRES", "LIGHTS", "BELTS"));
        mvc.perform(authPost(kumar, "/api/driver/duty/start", body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CHECKLIST_INCOMPLETE"))
                .andExpect(jsonPath("$.message").value(containsString("Fuel level")));

        body = new java.util.HashMap<>(startDutyBody("C-12"));
        body.put("startOdometer", 100);
        mvc.perform(authPost(kumar, "/api/driver/duty/start", body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ODOMETER_BELOW_LAST"));

        mvc.perform(authPost(kumar, "/api/driver/duty/start", Map.of("vehicleId", vehicleId("C-12"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field").value(org.hamcrest.Matchers.hasItems("startOdometer", "checklist")));

        mvc.perform(authPost(kumar, "/api/driver/duty/start", startDutyBody("C-12"))).andExpect(status().isOk());
        mvc.perform(authPost(kumar, "/api/driver/duty/start", startDutyBody("C-15")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_ON_DUTY"));
    }

    @Test
    @DisplayName("Wrong OTPs are counted even though the request fails, and lock boarding after five")
    void otpBruteForceIsCapped() throws Exception {
        String driver = login("DRV-2281");
        mvc.perform(authPost(driver, "/api/driver/duty/start", startDutyBody("C-12"))).andExpect(status().isOk());
        long tripId = read(mvc.perform(authGet(driver, "/api/driver/queue")).andReturn()).at("/trip/tripId").asLong();
        long iyer = jdbc.queryForObject("SELECT id FROM booking WHERE booking_code = 'PR-9317MI'", Long.class);

        // Boarding before arriving is refused.
        mvc.perform(authPost(driver, "/api/driver/trips/{id}/board", Map.of("bookingId", iyer, "otp", "9317"), tripId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_AT_PICKUP"));
        mvc.perform(authPost(driver, "/api/driver/trips/{id}/arrive", Map.of("stopSeq", 1), tripId)).andExpect(status().isOk());

        for (int i = 1; i <= 5; i++) {
            mvc.perform(authPost(driver, "/api/driver/trips/{id}/board", Map.of("bookingId", iyer, "otp", "0000"), tripId))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("OTP_INVALID"));
            // noRollbackFor: the failed attempt is still recorded.
            assertThat(jdbc.queryForObject("SELECT otp_attempts FROM booking WHERE id = ?", Integer.class, iyer)).isEqualTo(i);
        }
        // Even the right code is now refused until the desk verifies the rider.
        mvc.perform(authPost(driver, "/api/driver/trips/{id}/board", Map.of("bookingId", iyer, "otp", "9317"), tripId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OTP_LOCKED"));
    }

    @Test
    @DisplayName("No-show only after the configured wait; it counts toward the booking pause")
    void noShowAfterWait() throws Exception {
        String driver = login("DRV-2281");
        mvc.perform(authPost(driver, "/api/driver/duty/start", startDutyBody("C-12"))).andExpect(status().isOk());
        long tripId = read(mvc.perform(authGet(driver, "/api/driver/queue")).andReturn()).at("/trip/tripId").asLong();
        long banerjee = jdbc.queryForObject("SELECT id FROM booking WHERE booking_code = 'PR-5540SB'", Long.class);
        long iyer = jdbc.queryForObject("SELECT id FROM booking WHERE booking_code = 'PR-9317MI'", Long.class);
        mvc.perform(authPost(driver, "/api/driver/trips/{id}/arrive", Map.of("stopSeq", 1), tripId)).andExpect(status().isOk());

        mvc.perform(authPost(driver, "/api/driver/trips/{id}/no-show", Map.of("bookingId", banerjee), tripId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_SHOW_TOO_EARLY"));
        // Leaving with a rider still waiting is refused too.
        mvc.perform(authPost(driver, "/api/driver/trips/{id}/arrive", Map.of("stopSeq", 3), tripId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_ARRIVE"));

        clock.advance(Duration.ofMinutes(6));
        mvc.perform(authPost(driver, "/api/driver/trips/{id}/no-show", Map.of("bookingId", banerjee), tripId))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT status FROM booking WHERE id = ?", String.class, banerjee)).isEqualTo("NO_SHOW");

        // With M. Iyer marked a no-show too, nobody is left: the trip closes as CANCELLED and bills nothing.
        mvc.perform(authPost(driver, "/api/driver/trips/{id}/no-show", Map.of("bookingId", iyer), tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trip").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT status FROM trip WHERE id = ?", String.class, tripId)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cost_allocation WHERE trip_id = ?", Integer.class, tripId)).isZero();
    }

    @Test
    @DisplayName("A request no cab could serve within 90 minutes is closed, not served hours late")
    void staleRequestsExpire() throws Exception {
        clock.advance(Duration.ofMinutes(95));
        dispatchService.dispatchPending();
        Map<String, Object> iyer = jdbc.queryForMap("SELECT status, cancel_reason FROM booking WHERE booking_code = 'PR-9317MI'");
        assertThat(iyer.get("status")).isEqualTo("CANCELLED");
        assertThat((String) iyer.get("cancel_reason")).contains("No cab became free within 90 min");
        // Scheduled rides and approvals are not requests yet and are untouched.
        assertThat(jdbc.queryForObject("SELECT status FROM booking WHERE booking_code = 'PR-4AR08S'", String.class)).isEqualTo("SCHEDULED");
        assertThat(jdbc.queryForObject("SELECT status FROM booking WHERE booking_code = 'PR-2K8EXP'", String.class)).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    @DisplayName("Three no-shows in 30 days pause booking")
    void bookingPausedAfterNoShows() throws Exception {
        mvc.perform(authPost(login("LHS-52287"), "/api/rider/bookings", bookingBody("Gate 2", "Admin Block", "SHARED", 1, "H. Patel")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_PAUSED"));
    }

    @Test
    @DisplayName("Breakdown mid-trip: vehicle off-road, riders re-matched from the last stop reached")
    void breakdownRematchesRiders() throws Exception {
        String driver = login("DRV-2281");
        mvc.perform(authPost(driver, "/api/driver/duty/start", startDutyBody("C-12"))).andExpect(status().isOk());
        long tripId = read(mvc.perform(authGet(driver, "/api/driver/queue")).andReturn()).at("/trip/tripId").asLong();
        mvc.perform(authPost(driver, "/api/driver/trips/{id}/arrive", Map.of("stopSeq", 1), tripId)).andExpect(status().isOk());
        long iyer = jdbc.queryForObject("SELECT id FROM booking WHERE booking_code = 'PR-9317MI'", Long.class);
        mvc.perform(authPost(driver, "/api/driver/trips/{id}/board", Map.of("bookingId", iyer, "otp", "9317"), tripId))
                .andExpect(status().isOk());

        JsonNode result = read(mvc.perform(authPost(driver, "/api/driver/issues",
                        Map.of("type", "BREAKDOWN", "description", "Rear right tyre puncture")))
                .andExpect(status().isOk()).andReturn());
        assertThat(result.get("ridersRematched").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT status FROM vehicle WHERE code = 'C-12'", String.class)).isEqualTo("OFF_ROAD");
        assertThat(jdbc.queryForObject("SELECT status FROM trip WHERE id = ?", String.class, tripId)).isEqualTo("CANCELLED");
        Map<String, Object> rematched = jdbc.queryForMap("SELECT status, trip_id, from_seq FROM booking WHERE id = ?", iyer);
        assertThat(rematched.get("status")).isEqualTo("REQUESTED");
        assertThat(rematched.get("trip_id")).isNull();
        assertThat(rematched.get("from_seq")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alert WHERE alert_type = 'BREAKDOWN' AND vehicle_id = ? AND status = 'OPEN'",
                Integer.class, vehicleId("C-12"))).isEqualTo(1);
    }

    @Test
    @DisplayName("Resequencing is refused while the route has live rides, and works when idle")
    void resequenceRules() throws Exception {
        String admin = login("admin");
        JsonNode r1 = read(mvc.perform(authGet(admin, "/api/routes/{id}", 1)).andReturn());
        Map<String, Object> body = Map.of("version", r1.get("version").asLong(), "stops", List.of(
                Map.of("stopId", stopId("Gate 2"), "legMinutes", 0, "legKm", 0),
                Map.of("stopId", stopId("Coke Plant"), "legMinutes", 8, "legKm", 1.9),
                Map.of("stopId", stopId("Admin Block"), "legMinutes", 6, "legKm", 1.4),
                Map.of("stopId", stopId("Blast Furnace"), "legMinutes", 7, "legKm", 1.6),
                Map.of("stopId", stopId("Gate 2"), "legMinutes", 9, "legKm", 2.1)));
        // Route 1 has riders waiting at Gate 2 in the seed.
        mvc.perform(authPut(admin, "/api/admin/routes/{id}/stops", body, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUTE_IN_USE"));

        jdbc.update("UPDATE booking SET status = 'CANCELLED' WHERE route_id = 1 AND status IN ('REQUESTED','PENDING_APPROVAL','SCHEDULED')");
        mvc.perform(authPut(admin, "/api/admin/routes/{id}/stops", body, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stops[1].stopName").value("Coke Plant"))
                .andExpect(jsonPath("$.stops[2].stopName").value("Admin Block"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM route_stop WHERE route_id = 1", Integer.class)).isEqualTo(5);
    }

    @Test
    @DisplayName("Riders cannot see each other's bookings or reach desk endpoints")
    void accessControl() throws Exception {
        String raghavan = login("LHS-40218");
        long iyersBooking = jdbc.queryForObject("SELECT id FROM booking WHERE booking_code = 'PR-9317MI'", Long.class);
        mvc.perform(authGet(raghavan, "/api/rider/bookings/{id}", iyersBooking)).andExpect(status().isNotFound());
        mvc.perform(authGet(raghavan, "/api/desk/board")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(authGet(login("desk"), "/api/billing/summary")).andExpect(status().isForbidden());
        mvc.perform(authGet("not-a-token", "/api/rider/home")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
}
