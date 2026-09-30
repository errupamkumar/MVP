package com.srmecotech.plantride;

import com.fasterxml.jackson.databind.JsonNode;
import com.srmecotech.plantride.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The 3-rider cap is a hard rule: sequentially, under a race, and through a desk override. */
class OccupancyAndConcurrencyIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("A cab at its cap is never offered a fourth rider")
    void fourthRiderIsRefused() throws Exception {
        String driver = login("DRV-2281");
        mvc.perform(authPost(driver, "/api/driver/duty/start", startDutyBody("C-12"))).andExpect(status().isOk());
        mvc.perform(authPost(login("LHS-40218"), "/api/rider/bookings", bookingBody("Gate 2", "Blast Furnace", "SHARED", 1, "A. Raghavan")))
                .andExpect(jsonPath("$.vehicle.code").value("C-12"));

        JsonNode fourth = read(mvc.perform(authPost(login("LHS-44190"), "/api/rider/bookings",
                        bookingBody("Gate 2", "Admin Block", "SHARED", 1, "N. Gupta")))
                .andExpect(status().isCreated()).andReturn());
        assertThat(fourth.get("status").asText()).isEqualTo("REQUESTED");
        assertThat(fourth.get("matchNote").asText()).contains("C-12 is at its 3-rider cap");
    }

    @Test
    @DisplayName("Two riders racing for the last seat: exactly one gets it")
    void raceForTheLastSeat() throws Exception {
        String driver = login("DRV-2281");
        mvc.perform(authPost(driver, "/api/driver/duty/start", startDutyBody("C-12"))).andExpect(status().isOk());
        // C-12 now carries M. Iyer and S. Banerjee: one seat left on Gate 2 -> Coke Plant.
        String raghavan = login("LHS-40218");
        String gupta = login("LHS-44190");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<JsonNode>> calls = new ArrayList<>();
        for (Object[] r : List.of(new Object[]{raghavan, "A. Raghavan"}, new Object[]{gupta, "N. Gupta"})) {
            calls.add(() -> {
                start.await();
                MvcResult res = mvc.perform(authPost((String) r[0], "/api/rider/bookings",
                        bookingBody("Gate 2", "Coke Plant", "SHARED", 1, (String) r[1]))).andReturn();
                return read(res);
            });
        }
        List<Future<JsonNode>> futures = new ArrayList<>();
        for (Callable<JsonNode> c : calls) {
            futures.add(pool.submit(c));
        }
        start.countDown();
        List<String> statuses = new ArrayList<>();
        for (Future<JsonNode> f : futures) {
            statuses.add(f.get(30, TimeUnit.SECONDS).get("status").asText());
        }
        pool.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder("ASSIGNED", "REQUESTED");
        Integer onC12 = jdbc.queryForObject("""
                SELECT COALESCE(SUM(b.seats), 0) FROM booking b JOIN trip t ON t.id = b.trip_id
                WHERE t.vehicle_id = ? AND t.status IN ('PLANNED','IN_PROGRESS') AND b.status IN ('ASSIGNED','ONBOARD')
                """, Integer.class, vehicleId("C-12"));
        assertThat(onC12).isEqualTo(3);
    }

    @Test
    @DisplayName("The desk override still respects the cap, and explains why")
    void deskOverrideCannotBreakTheCap() throws Exception {
        String driver = login("DRV-2281");
        mvc.perform(authPost(driver, "/api/driver/duty/start", startDutyBody("C-12"))).andExpect(status().isOk());
        mvc.perform(authPost(login("LHS-40218"), "/api/rider/bookings", bookingBody("Gate 2", "Blast Furnace", "SHARED", 1, "A. Raghavan")))
                .andExpect(jsonPath("$.status").value("ASSIGNED"));
        long waiting = read(mvc.perform(authPost(login("LHS-44190"), "/api/rider/bookings",
                bookingBody("Gate 2", "Admin Block", "SHARED", 1, "N. Gupta"))).andReturn()).get("id").asLong();

        String admin = login("admin");
        mvc.perform(authPost(admin, "/api/desk/bookings/{id}/assign", Map.of("vehicleId", vehicleId("C-12")), waiting))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MATCH_REJECTED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("3-rider cap")));

        JsonNode candidates = read(mvc.perform(authGet(admin, "/api/desk/bookings/{id}/candidates", waiting)).andReturn());
        assertThat(candidates.findValuesAsText("reason")).anyMatch(r -> r.contains("C-21 driver A. Singh would pass the duty-hour limit"));
    }

    @Test
    @DisplayName("Raising the cap through the admin screen lets a waiting rider in, with no release")
    void capIsConfiguration() throws Exception {
        String driver = login("DRV-2281");
        mvc.perform(authPost(driver, "/api/driver/duty/start", startDutyBody("C-12"))).andExpect(status().isOk());
        mvc.perform(authPost(login("LHS-40218"), "/api/rider/bookings", bookingBody("Gate 2", "Blast Furnace", "SHARED", 1, "A. Raghavan")));
        long waiting = read(mvc.perform(authPost(login("LHS-44190"), "/api/rider/bookings",
                bookingBody("Gate 2", "Admin Block", "SHARED", 1, "N. Gupta"))).andReturn()).get("id").asLong();

        String admin = login("admin");
        JsonNode route = read(mvc.perform(authGet(admin, "/api/routes/{id}", 1)).andReturn());
        mvc.perform(authPut(admin, "/api/admin/routes/{id}/rules", Map.of(
                        "maxPassengers", 4, "maxWaitMin", 5, "maxDetourMin", 7, "speedLimitKmh", 20,
                        "frequencyMin", 20, "version", route.get("version").asLong()), 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxPassengers").value(4));

        // The rule change publishes a dispatch event: the waiting rider joins C-12 immediately.
        assertThat(jdbc.queryForObject("SELECT status FROM booking WHERE id = ?", String.class, waiting)).isEqualTo("ASSIGNED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = 'ROUTE_RULES_UPDATED' AND details LIKE '%max passengers 3 → 4%'",
                Integer.class)).isEqualTo(1);

        // A second save with the old version is refused instead of silently overwriting.
        mvc.perform(authPut(admin, "/api/admin/routes/{id}/rules", Map.of(
                        "maxPassengers", 2, "maxWaitMin", 5, "maxDetourMin", 7, "speedLimitKmh", 20,
                        "frequencyMin", 20, "version", route.get("version").asLong()), 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_DATA"));
    }
}
