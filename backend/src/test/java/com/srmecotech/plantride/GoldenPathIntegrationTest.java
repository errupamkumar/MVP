package com.srmecotech.plantride;

import com.fasterxml.jackson.databind.JsonNode;
import com.srmecotech.plantride.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Deck 2, slide 16: one booking followed across the rider, driver and desk
 * lanes, asserting every handover and the money at the end.
 */
class GoldenPathIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("Book, pool, board by OTP, drop, bill: the whole golden path")
    void goldenPath() throws Exception {
        String driver = login("DRV-2281");
        String rider = login("LHS-40218");
        String admin = login("admin");

        // 1. Driver signs on; the two riders waiting at Gate 2 are pooled onto C-12 straight away.
        mvc.perform(authPost(driver, "/api/driver/duty/start", startDutyBody("C-12")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        JsonNode queue = read(mvc.perform(authGet(driver, "/api/driver/queue")).andExpect(status().isOk()).andReturn());
        long tripId = queue.at("/trip/tripId").asLong();
        assertThat(queue.at("/trip/stops/0/stopName").asText()).isEqualTo("Gate 2");
        assertThat(queue.at("/trip/stops/0/pickups")).hasSize(2);

        // 2. Rider sees C-12 with 2 of 3 seats taken, books, and gets vehicle, driver and OTP.
        JsonNode options = read(mvc.perform(authPost(rider, "/api/rider/ride-options",
                Map.of("fromStopId", stopId("Gate 2"), "toStopId", stopId("Blast Furnace"), "seats", 1))).andReturn());
        JsonNode shared = findOption(options, "SHARED");
        assertThat(shared.get("vehicleCode").asText()).isEqualTo("C-12");
        assertThat(shared.get("seatsTaken").asInt()).isEqualTo(2);
        assertThat(shared.get("capacity").asInt()).isEqualTo(3);

        JsonNode booking = read(mvc.perform(authPost(rider, "/api/rider/bookings",
                        bookingBody("Gate 2", "Blast Furnace", "SHARED", 1, "A. Raghavan"))
                        .header("Idempotency-Key", "golden-path-1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.vehicle.registrationNo").value("MH 12 AB 4412"))
                .andExpect(jsonPath("$.driver.name").value("S. Kumar"))
                .andReturn());
        long bookingId = booking.get("id").asLong();
        String otp = booking.get("otp").asText();
        assertThat(otp).matches("\\d{4}");

        // 3. Public tracking link works without login and leaks no personal data.
        String token = booking.get("trackingToken").asText();
        String tracking = mvc.perform(get("/api/public/track/{t}", token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(tracking).contains("C-12").doesNotContain("Raghavan").doesNotContain("+91");

        // 4. Driver arrives at Gate 2 and boards all three by OTP.
        mvc.perform(authPost(driver, "/api/driver/trips/{id}/arrive", Map.of("stopSeq", 1), tripId)).andExpect(status().isOk());
        JsonNode riderView = read(mvc.perform(authGet(rider, "/api/rider/bookings/{id}", bookingId)).andReturn());
        assertThat(riderView.at("/progress/stage").asText()).isEqualTo("AT_PICKUP");

        long iyer = bookingIdFor("M. Iyer");
        long banerjee = bookingIdFor("S. Banerjee");
        for (Object[] b : List.of(new Object[]{bookingId, otp}, new Object[]{iyer, "9317"}, new Object[]{banerjee, "5540"})) {
            mvc.perform(authPost(driver, "/api/driver/trips/{id}/board", Map.of("bookingId", b[0], "otp", b[1]), tripId))
                    .andExpect(status().isOk());
        }
        assertThat(jdbc.queryForObject("SELECT status FROM trip WHERE id = ?", String.class, tripId)).isEqualTo("IN_PROGRESS");

        // 5. Drops: Coke Plant (M. Iyer), then Blast Furnace closes the trip.
        mvc.perform(authPost(driver, "/api/driver/trips/{id}/arrive", Map.of("stopSeq", 3), tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trip.onBoard").value(2));
        mvc.perform(authPost(driver, "/api/driver/trips/{id}/arrive", Map.of("stopSeq", 4), tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trip").doesNotExist());

        // 6. Trip costed on GPS route km and split by distance; shares add up exactly.
        Map<String, Object> trip = jdbc.queryForMap("SELECT status, distance_km, cost_amount FROM trip WHERE id = ?", tripId);
        assertThat(trip.get("status")).isEqualTo("COMPLETED");
        assertThat((BigDecimal) trip.get("distance_km")).isEqualByComparingTo("3.40");
        assertThat((BigDecimal) trip.get("cost_amount")).isEqualByComparingTo("61.20");
        BigDecimal allocated = jdbc.queryForObject("SELECT SUM(amount) FROM cost_allocation WHERE trip_id = ?", BigDecimal.class, tripId);
        assertThat(allocated).isEqualByComparingTo("61.20");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cost_allocation WHERE trip_id = ?", Integer.class, tripId)).isEqualTo(3);

        JsonNode done = read(mvc.perform(authGet(rider, "/api/rider/bookings/{id}", bookingId)).andReturn());
        assertThat(done.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(done.get("costCentreCode").asText()).isEqualTo("CC-4471");
        assertThat(done.get("distanceKm").decimalValue()).isEqualByComparingTo("3.4");
        // Distance rule: 61.20 x 3.4 / (2.3 + 3.4 + 3.4), with the rounding residue on the last share.
        assertThat(done.get("costAmount").decimalValue()).isEqualByComparingTo("22.86");
        assertThat(done.get("otp").isNull()).as("OTP is hidden once the ride is closed").isTrue();

        // 7. The tracking link expires; the rider rates; the desk sees it in billing and the audit trail.
        mvc.perform(get("/api/public/track/{t}", token)).andExpect(status().isGone());
        mvc.perform(authPost(rider, "/api/rider/bookings/{id}/rating",
                        Map.of("rating", 5, "tags", List.of("ON_TIME", "CLEAN_CAB"), "note", "Smooth"), bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canRate").value(false));
        JsonNode billing = read(mvc.perform(authGet(admin, "/api/billing/summary")).andExpect(status().isOk()).andReturn());
        assertThat(billing.get("costCentres").findValuesAsText("code")).contains("CC-4471");
        String csv = mvc.perform(authGet(admin, "/api/billing/export")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(csv).contains(booking.get("bookingCode").asText());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = 'TRIP_COMPLETED' AND entity_id = ?",
                Integer.class, tripId)).isEqualTo(1);

        // 8. Sign-off: 1 trip, 3 riders, GPS km agrees with the odometer.
        int opening = jdbc.queryForObject("SELECT start_odometer FROM duty WHERE status = 'ACTIVE' AND vehicle_id = ?",
                Integer.class, vehicleId("C-12"));
        mvc.perform(authPost(driver, "/api/driver/duty/end", Map.of("endOdometer", opening + 6, "fuelLitres", 2.5)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripsCompleted").value(1))
                .andExpect(jsonPath("$.ridersCarried").value(3))
                .andExpect(jsonPath("$.odometerCheck").value("MATCHES"));
    }

    @Test
    @DisplayName("A retried Confirm with the same Idempotency-Key never books twice")
    void idempotentBooking() throws Exception {
        String rider = login("LHS-40218");
        Map<String, Object> body = bookingBody("Gate 2", "Admin Block", "SHARED", 1, "A. Raghavan");
        long first = read(mvc.perform(authPost(rider, "/api/rider/bookings", body).header("Idempotency-Key", "tap-1"))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
        long second = read(mvc.perform(authPost(rider, "/api/rider/bookings", body).header("Idempotency-Key", "tap-1"))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
        assertThat(second).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE client_request_id = 'tap-1'", Integer.class)).isEqualTo(1);
    }

    private JsonNode findOption(JsonNode options, String type) {
        for (JsonNode o : options.get("options")) {
            if (o.get("type").asText().equals(type)) {
                return o;
            }
        }
        throw new AssertionError("no " + type + " option in " + options);
    }

    private long bookingIdFor(String riderName) {
        return jdbc.queryForObject("SELECT id FROM booking WHERE rider_name = ? AND status = 'ASSIGNED'", Long.class, riderName);
    }
}
