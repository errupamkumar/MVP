package com.srmecotech.plantride;

import com.fasterxml.jackson.databind.JsonNode;
import com.srmecotech.plantride.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The driver's phone moves the cab on its riders' screens; a waiting rider
 * shares where they are with the driver coming for them, until they board.
 * Fixes outside the plant never move anyone.
 */
class LiveLocationIntegrationTest extends IntegrationTestBase {

    /** About 360 m from Gate 2 on the road to Admin Block: outside Gate 2's 40 m arrival radius. */
    private static final double NEAR_GATE2_LAT = 18.6240;
    private static final double NEAR_GATE2_LNG = 73.8003;
    /** About 30 m from the Gate 2 pickup point, inside its arrival radius. */
    private static final double AT_GATE2_LAT = 18.6214;
    private static final double AT_GATE2_LNG = 73.7987;

    @Test
    @DisplayName("A driver's GPS ping moves the cab on the rider's screen, with distance to the pickup")
    void driverPingMovesCabForRider() throws Exception {
        String driver = signOnC12();
        String iyer = login("LHS-31890");
        long booking = bookingIdFor("M. Iyer");

        JsonNode before = rider(iyer, booking);
        assertThat(before.at("/cabLocation/live").asBoolean()).isFalse();
        assertThat(before.at("/cabLocation/nearStopName").asText()).isEqualTo("Gate 2");
        assertThat(before.at("/driver/fleet").asText()).isEqualTo("LHS own fleet");
        assertThat(before.at("/progress/stops/0/latitude").asDouble()).isEqualTo(18.6212);

        long version = jdbc.queryForObject("SELECT version FROM vehicle WHERE code = 'C-12'", Long.class);
        mvc.perform(authPost(driver, "/api/driver/location", ping(NEAR_GATE2_LAT, NEAR_GATE2_LNG, 12.0, 8, 90, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outsideServiceArea").value(false))
                .andExpect(jsonPath("$.autoArrivedAt").isEmpty());
        // A ping moves the cab without bumping its version, so it never makes an "Arrived" tap stale.
        assertThat(jdbc.queryForObject("SELECT version FROM vehicle WHERE code = 'C-12'", Long.class)).isEqualTo(version);

        JsonNode cab = rider(iyer, booking).get("cabLocation");
        assertThat(cab.get("vehicleCode").asText()).isEqualTo("C-12");
        assertThat(cab.get("live").asBoolean()).isTrue();
        assertThat(cab.get("latitude").asDouble()).isEqualTo(NEAR_GATE2_LAT);
        assertThat(cab.get("speedKmh").asDouble()).isEqualTo(12.0);
        assertThat(cab.get("heading").asInt()).isEqualTo(90);
        assertThat(cab.get("accuracyM").asInt()).isEqualTo(8);
        assertThat(cab.get("secondsAgo").asLong()).isBetween(0L, 2L);
        assertThat(cab.get("towardsStopName").asText()).isEqualTo("Gate 2");
        assertThat(cab.get("metresToStop").asInt()).isBetween(300, 450);

        JsonNode queue = read(mvc.perform(authGet(driver, "/api/driver/queue")).andExpect(status().isOk()).andReturn());
        assertThat(queue.at("/trip/cab/live").asBoolean()).isTrue();
        assertThat(queue.at("/trip/cab/towardsStopName").asText()).isEqualTo("Gate 2");
        assertThat(queue.at("/trip/stops/0/longitude").asDouble()).isEqualTo(73.7985);

        // Three quiet minutes: still shown where it was last seen, but no longer live.
        clock.advance(Duration.ofMinutes(3));
        JsonNode quiet = rider(iyer, booking).get("cabLocation");
        assertThat(quiet.get("live").asBoolean()).isFalse();
        assertThat(quiet.get("latitude").asDouble()).isEqualTo(NEAR_GATE2_LAT);
        assertThat(quiet.get("speedKmh").isNull()).isTrue();
    }

    @Test
    @DisplayName("A fix older than the cab's last one, or outside the plant, does not move the cab")
    void staleAndOutsideFixesIgnored() throws Exception {
        String driver = signOnC12();
        mvc.perform(authPost(driver, "/api/driver/location", ping(NEAR_GATE2_LAT, NEAR_GATE2_LNG, 0.0, null, null, 0)))
                .andExpect(status().isOk());

        // An offline buffer flushing a 5-minute-old fix: kept in history, live position unchanged.
        mvc.perform(authPost(driver, "/api/driver/location", ping(18.6301, 73.8171, 0.0, null, null, 300)))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT last_latitude FROM vehicle WHERE code = 'C-12'", Double.class))
                .isEqualTo(NEAR_GATE2_LAT);

        // A laptop in Delhi running the web app: ignored outright.
        mvc.perform(authPost(driver, "/api/driver/location", ping(28.6139, 77.2090, 0.0, null, null, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outsideServiceArea").value(true));
        assertThat(jdbc.queryForObject("SELECT last_latitude FROM vehicle WHERE code = 'C-12'", Double.class))
                .isEqualTo(NEAR_GATE2_LAT);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gps_ping", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("A waiting rider's location reaches their driver, and is deleted when they board")
    void riderSharesLocationUntilBoarding() throws Exception {
        String driver = signOnC12();
        String iyer = login("LHS-31890");
        long booking = bookingIdFor("M. Iyer");

        mvc.perform(authPost(iyer, "/api/rider/bookings/{id}/location", fix(AT_GATE2_LAT, AT_GATE2_LNG, 12, 3), booking))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shared").value(true))
                .andExpect(jsonPath("$.sharedWith").value("S. Kumar · C-12"))
                .andExpect(jsonPath("$.pickupName").value("Gate 2"))
                .andExpect(jsonPath("$.atPickup").value(true));

        JsonNode queue = read(mvc.perform(authGet(driver, "/api/driver/queue")).andExpect(status().isOk()).andReturn());
        JsonNode iyerAtGate = pickup(queue, "M. Iyer");
        JsonNode location = iyerAtGate.get("location");
        assertThat(location.get("live").asBoolean()).isTrue();
        assertThat(location.get("atPickup").asBoolean()).isTrue();
        assertThat(location.get("pickupName").asText()).isEqualTo("Gate 2");
        assertThat(location.get("metresToPickup").asInt()).isBetween(20, 40);
        assertThat(location.get("accuracyM").asInt()).isEqualTo(12);
        assertThat(location.get("secondsAgo").asLong()).isBetween(2L, 5L);
        // C-12 is parked at Gate 2.
        assertThat(location.get("metresFromCab").asInt()).isBetween(20, 40);
        assertThat(pickup(queue, "S. Banerjee").get("location").isNull()).isTrue();

        // The rider sees what they are sharing.
        assertThat(rider(iyer, booking).at("/myLocation/atPickup").asBoolean()).isTrue();

        // Nobody else can write a position onto someone's booking.
        mvc.perform(authPost(login("LHS-45512"), "/api/rider/bookings/{id}/location", fix(AT_GATE2_LAT, AT_GATE2_LNG, 5, 0), booking))
                .andExpect(status().isNotFound());

        // Boarding deletes it; the cab's position is the rider's from here on.
        long tripId = queue.at("/trip/tripId").asLong();
        mvc.perform(authPost(driver, "/api/driver/trips/{id}/arrive", Map.of("stopSeq", 1), tripId)).andExpect(status().isOk());
        mvc.perform(authPost(driver, "/api/driver/trips/{id}/board", Map.of("bookingId", booking, "otp", "9317"), tripId))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rider_location WHERE booking_id = ?", Integer.class, booking)).isZero();
        assertThat(rider(iyer, booking).get("myLocation").isNull()).isTrue();
        assertThat(rider(iyer, booking).at("/cabLocation/towardsStopName").asText()).isEqualTo("Coke Plant");

        mvc.perform(authPost(iyer, "/api/rider/bookings/{id}/location", fix(AT_GATE2_LAT, AT_GATE2_LNG, 5, 0), booking))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_WAITING_FOR_CAB"));
    }

    @Test
    @DisplayName("A rider can stop sharing; old fixes stop showing; no sharing before a cab is assigned or from outside the plant")
    void riderSharingLimits() throws Exception {
        String raghavan = login("LHS-40218");
        long waiting = read(mvc.perform(authPost(raghavan, "/api/rider/bookings",
                        bookingBody("Gate 2", "Blast Furnace", "SHARED", 1, "A. Raghavan")))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
        // No cab yet: there is no driver to share with.
        mvc.perform(authPost(raghavan, "/api/rider/bookings/{id}/location", fix(AT_GATE2_LAT, AT_GATE2_LNG, 10, 0), waiting))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_WAITING_FOR_CAB"));

        String driver = signOnC12();
        String iyer = login("LHS-31890");
        long booking = bookingIdFor("M. Iyer");

        mvc.perform(authPost(iyer, "/api/rider/bookings/{id}/location", fix(28.6139, 77.2090, 10, 0), booking))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shared").value(false))
                .andExpect(jsonPath("$.outsideServiceArea").value(true));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rider_location", Integer.class)).isZero();

        mvc.perform(authPost(iyer, "/api/rider/bookings/{id}/location", fix(NEAR_GATE2_LAT, NEAR_GATE2_LNG, 10, 0), booking))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atPickup").value(false));
        assertThat(pickup(queue(driver), "M. Iyer").at("/location/metresToPickup").asInt()).isBetween(300, 450);

        clock.advance(Duration.ofMinutes(3));
        assertThat(pickup(queue(driver), "M. Iyer").at("/location/live").asBoolean()).isFalse();
        clock.advance(Duration.ofMinutes(13));
        assertThat(pickup(queue(driver), "M. Iyer").get("location").isNull()).isTrue();

        clock.reset();
        mvc.perform(delete("/api/rider/bookings/{id}/location", booking).header("Authorization", "Bearer " + iyer))
                .andExpect(status().isNoContent());
        assertThat(pickup(queue(driver), "M. Iyer").get("location").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rider_location", Integer.class)).isZero();
    }

    // ------------------------------------------------------------------ helpers

    private String signOnC12() throws Exception {
        String driver = login("DRV-2281");
        mvc.perform(authPost(driver, "/api/driver/duty/start", startDutyBody("C-12"))).andExpect(status().isOk());
        return driver;
    }

    private JsonNode rider(String token, long bookingId) throws Exception {
        return read(mvc.perform(authGet(token, "/api/rider/bookings/{id}", bookingId)).andExpect(status().isOk()).andReturn());
    }

    private JsonNode queue(String driver) throws Exception {
        return read(mvc.perform(authGet(driver, "/api/driver/queue")).andExpect(status().isOk()).andReturn());
    }

    private long bookingIdFor(String riderName) {
        return jdbc.queryForObject("SELECT id FROM booking WHERE rider_name = ? AND status = 'ASSIGNED'", Long.class, riderName);
    }

    private static JsonNode pickup(JsonNode queue, String riderName) {
        return StreamSupport.stream(queue.at("/trip/stops").spliterator(), false)
                .flatMap(s -> StreamSupport.stream(s.get("pickups").spliterator(), false))
                .filter(r -> riderName.equals(r.get("riderName").asText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(riderName + " not in the queue: " + queue));
    }

    private static Map<String, Object> ping(double lat, double lng, Double speed, Integer accuracy, Integer heading, int ageSeconds) {
        Map<String, Object> body = new HashMap<>();
        body.put("latitude", lat);
        body.put("longitude", lng);
        body.put("speedKmh", speed);
        body.put("accuracyM", accuracy);
        body.put("heading", heading);
        body.put("ageSeconds", ageSeconds);
        return body;
    }

    private static Map<String, Object> fix(double lat, double lng, int accuracy, int ageSeconds) {
        return Map.of("latitude", lat, "longitude", lng, "accuracyM", accuracy, "ageSeconds", ageSeconds);
    }
}
