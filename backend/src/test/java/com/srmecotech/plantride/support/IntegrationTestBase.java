package com.srmecotech.plantride.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srmecotech.plantride.safety.DocumentExpiryMonitor;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.sql.DataSource;
import java.time.ZoneId;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Full application against MySQL. Every test starts from the demo seed
 * (schema.sql + data.sql reloaded), so tests are independent and can
 * commit real transactions, which the locking and constraint tests need.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTestBase.TestClockConfig.class)
public abstract class IntegrationTestBase {

    public static final String PASSWORD = "Plant@123";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected MutableClock clock;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private DocumentExpiryMonitor documentExpiryMonitor;

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(ZoneId.of("Asia/Kolkata"));
        }
    }

    @BeforeEach
    void reseed() {
        clock.reset();
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("schema.sql"), new ClassPathResource("data.sql"));
        populator.setSqlScriptEncoding("UTF-8");
        populator.execute(dataSource);
        documentExpiryMonitor.scan();
    }

    // ------------------------------------------------------------------ helpers

    protected String login(String username) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", PASSWORD))))
                .andReturn();
        if (result.getResponse().getStatus() != 200) {
            throw new IllegalStateException("login failed for " + username + ": " + result.getResponse().getContentAsString());
        }
        return read(result).get("token").asText();
    }

    protected MockHttpServletRequestBuilder authGet(String token, String url, Object... vars) {
        return get(url, vars).header("Authorization", "Bearer " + token);
    }

    protected MockHttpServletRequestBuilder authPost(String token, String url, Object body, Object... vars) throws Exception {
        return post(url, vars).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body == null ? "{}" : json.writeValueAsString(body));
    }

    protected MockHttpServletRequestBuilder authPut(String token, String url, Object body, Object... vars) throws Exception {
        return put(url, vars).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body));
    }

    protected JsonNode read(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    protected long stopId(String name) {
        return jdbc.queryForObject("SELECT id FROM stop WHERE name = ?", Long.class, name);
    }

    protected long vehicleId(String code) {
        return jdbc.queryForObject("SELECT id FROM vehicle WHERE code = ?", Long.class, code);
    }

    protected Map<String, Object> startDutyBody(String vehicleCode) {
        int odometer = jdbc.queryForObject("SELECT odometer_km FROM vehicle WHERE code = ?", Integer.class, vehicleCode);
        return Map.of("vehicleId", vehicleId(vehicleCode), "startOdometer", odometer,
                "checklist", java.util.List.of("TYRES", "LIGHTS", "BELTS", "FUEL", "FIRST_AID"));
    }

    protected Map<String, Object> bookingBody(String from, String to, String rideType, int seats, String riderName) {
        java.util.HashMap<String, Object> body = new java.util.HashMap<>();
        body.put("fromStopId", stopId(from));
        body.put("toStopId", stopId(to));
        body.put("rideType", rideType);
        body.put("seats", seats);
        body.put("scheduledAt", null);
        body.put("riderName", riderName);
        body.put("riderPhone", "+91 98450 00000");
        body.put("riderEmail", null);
        body.put("forGuest", false);
        return body;
    }
}
