package com.srmecotech.plantride.identity;

import com.srmecotech.plantride.common.security.JwtService;
import com.srmecotech.plantride.identity.dto.AuthDtos.LoginResponse;
import com.srmecotech.plantride.identity.dto.AuthDtos.UserSummary;
import com.srmecotech.plantride.support.WebMvcTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(WebMvcTestSupport.class)
class AuthControllerWebMvcTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AuthService authService;
    @MockitoBean
    JwtService jwtService;
    @MockitoBean
    AppUserRepository userRepository;

    @Test
    @DisplayName("Login is public and returns a token with the user's role")
    void login() throws Exception {
        when(authService.login(any())).thenReturn(new LoginResponse("jwt", Instant.parse("2026-09-22T20:35:00Z"),
                new UserSummary(1L, "LHS-40218", "A. Raghavan", Role.EMPLOYEE, null, null)));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"LHS-40218\",\"password\":\"Plant@123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt"))
                .andExpect(jsonPath("$.user.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.expiresAt").value("2026-09-22T20:35:00Z"));
    }

    @Test
    @DisplayName("Wrong password: 401 with one message that does not reveal whether the ID exists")
    void badCredentials() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("That ID and password do not match."));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody\",\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("That ID and password do not match."));
    }

    @Test
    @DisplayName("Blank fields are a 400 with field messages the login screen can show")
    void blankFields() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
    }
}
