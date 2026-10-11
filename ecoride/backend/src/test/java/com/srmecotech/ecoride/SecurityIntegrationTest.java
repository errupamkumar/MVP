package com.srmecotech.ecoride;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rider, driver and admin accounts are separate: each token opens only its own app's API. */
@AutoConfigureMockMvc
class SecurityIntegrationTest extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String login(String path, String body) throws Exception {
        String res = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(res);
        return node.get("token").asText();
    }

    @Test
    void eachRoleOnlyReachesItsOwnApi() throws Exception {
        String rider = login("/api/rider/auth/login", "{\"login\":\"9876500001\",\"password\":\"Rider@123\"}");
        String driver = login("/api/driver/auth/login", "{\"login\":\"9876600001\",\"password\":\"Driver@123\"}");
        String admin = login("/api/admin/auth/login", "{\"email\":\"admin@ecoride.in\",\"password\":\"Admin@123\"}");

        mvc.perform(get("/api/rider/me").header("Authorization", "Bearer " + rider)).andExpect(status().isOk());
        mvc.perform(get("/api/driver/state").header("Authorization", "Bearer " + rider)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + rider)).andExpect(status().isForbidden());

        mvc.perform(get("/api/driver/state").header("Authorization", "Bearer " + driver)).andExpect(status().isOk());
        mvc.perform(get("/api/rider/rides/current").header("Authorization", "Bearer " + driver)).andExpect(status().isForbidden());

        mvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
        mvc.perform(get("/api/geo/search?q=airport").header("Authorization", "Bearer " + admin)).andExpect(status().isOk());

        mvc.perform(get("/api/rider/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/rider/me").header("Authorization", "Bearer not-a-token")).andExpect(status().isUnauthorized());
    }

    @Test
    void riderCredentialsDoNotOpenTheDriverApp() throws Exception {
        mvc.perform(post("/api/driver/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"9876500001\",\"password\":\"Rider@123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    @Test
    void signupValidatesInput() throws Exception {
        mvc.perform(post("/api/rider/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A\",\"phone\":\"123\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/rider/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Asha\",\"phone\":\"12345\",\"password\":\"Secret@123\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PHONE_INVALID"));
    }

    @Test
    void aBlockedRiderLosesAccessAtOnce() throws Exception {
        long id = newRider();
        String phone = jdbc.queryForObject("SELECT phone FROM rider WHERE id = ?", String.class, id);
        String token = login("/api/rider/auth/login", "{\"login\":\"" + phone + "\",\"password\":\"Secret@123\"}");
        mvc.perform(get("/api/rider/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        jdbc.update("UPDATE rider SET status = 'BLOCKED' WHERE id = ?", id);
        mvc.perform(get("/api/rider/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/rider/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + phone + "\",\"password\":\"Secret@123\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_BLOCKED"));
    }
}
