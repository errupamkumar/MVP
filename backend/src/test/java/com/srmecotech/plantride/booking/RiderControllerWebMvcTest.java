package com.srmecotech.plantride.booking;

import com.srmecotech.plantride.booking.dto.RiderDtos.BookingDto;
import com.srmecotech.plantride.booking.dto.RiderDtos.CreateBookingRequest;
import com.srmecotech.plantride.booking.dto.RiderDtos.RideProgressDto;
import com.srmecotech.plantride.common.error.BusinessRuleException;
import com.srmecotech.plantride.common.security.AuthenticatedUser;
import com.srmecotech.plantride.common.security.JwtService;
import com.srmecotech.plantride.identity.AppUserRepository;
import com.srmecotech.plantride.identity.Role;
import com.srmecotech.plantride.masterdata.dto.MasterDataDtos.RouteRef;
import com.srmecotech.plantride.masterdata.dto.MasterDataDtos.StopRef;
import com.srmecotech.plantride.support.WebMvcTestSupport;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contract of POST /api/rider/bookings: status codes, validation envelope, role guard, idempotency header. */
@WebMvcTest(RiderController.class)
@Import(WebMvcTestSupport.class)
class RiderControllerWebMvcTest {

    private static final AuthenticatedUser RIDER = new AuthenticatedUser(1L, "LHS-40218", Role.EMPLOYEE, "A. Raghavan");
    private static final AuthenticatedUser DRIVER = new AuthenticatedUser(13L, "DRV-2281", Role.DRIVER, "S. Kumar");

    private static final String VALID = """
            {"fromStopId": 1, "toStopId": 4, "rideType": "SHARED", "seats": 1, "scheduledAt": null,
             "riderName": "A. Raghavan", "riderPhone": "+91 98450 41227", "riderEmail": "a.raghavan@lhs.co.in",
             "forGuest": false}
            """;

    @Autowired
    MockMvc mvc;

    @MockitoBean
    RiderBookingService service;
    @MockitoBean
    JwtService jwtService;
    @MockitoBean
    AppUserRepository userRepository;

    @BeforeEach
    void tokens() {
        when(jwtService.parse("rider")).thenReturn(RIDER);
        when(jwtService.parse("driver")).thenReturn(DRIVER);
        when(jwtService.parse("forged")).thenThrow(new JwtException("bad signature"));
        when(userRepository.existsByIdAndActiveTrue(anyLong())).thenReturn(true);
    }

    @Test
    @DisplayName("201 with the booking; the Idempotency-Key header reaches the service")
    void createsBooking() throws Exception {
        when(service.create(eq(RIDER), any(CreateBookingRequest.class), eq("key-1"))).thenReturn(sampleBooking());

        mvc.perform(post("/api/rider/bookings").header("Authorization", "Bearer rider").header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingCode").value("PR-ABC123"))
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.progress.stage").value("ON_THE_WAY"))
                // LocalDateTime travels as an ISO string, never as an array or epoch number.
                .andExpect(jsonPath("$.createdAt").value("2026-09-22T14:05:00"));
        verify(service).create(eq(RIDER), any(CreateBookingRequest.class), eq("key-1"));
    }

    @Test
    @DisplayName("400 VALIDATION_FAILED lists every bad field")
    void validationEnvelope() throws Exception {
        String bad = """
                {"fromStopId": 1, "toStopId": 4, "seats": 0, "riderName": "", "riderPhone": "call me", "forGuest": false}
                """;
        mvc.perform(post("/api/rider/bookings").header("Authorization", "Bearer rider")
                        .contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field").value(hasItems("rideType", "seats", "riderName", "riderPhone")));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("400 MALFORMED_REQUEST for broken JSON or an unknown enum value")
    void malformedBody() throws Exception {
        mvc.perform(post("/api/rider/bookings").header("Authorization", "Bearer rider")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID.replace("SHARED", "LIMO")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("409 carries the business rule's own code and message")
    void businessRule() throws Exception {
        when(service.create(any(), any(), any())).thenThrow(new BusinessRuleException("BOOKING_PAUSED", "Booking is paused."));
        mvc.perform(post("/api/rider/bookings").header("Authorization", "Bearer rider")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_PAUSED"))
                .andExpect(jsonPath("$.message").value("Booking is paused."))
                .andExpect(jsonPath("$.path").value("/api/rider/bookings"));
    }

    @Test
    @DisplayName("401 without a token or with a forged one; 403 for the wrong role; all as JSON")
    void securityEnvelope() throws Exception {
        mvc.perform(get("/api/rider/home"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/rider/home").header("Authorization", "Bearer forged"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/rider/bookings").header("Authorization", "Bearer driver")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("A deactivated account is refused even with an unexpired token")
    void deactivatedUser() throws Exception {
        when(userRepository.existsByIdAndActiveTrue(1L)).thenReturn(false);
        mvc.perform(get("/api/rider/home").header("Authorization", "Bearer rider"))
                .andExpect(status().isUnauthorized());
    }

    private static BookingDto sampleBooking() {
        RideProgressDto progress = new RideProgressDto("ON_THE_WAY", "Cab on the way", 2, 17, "Gate 2", 2, 3, 3, List.of());
        return new BookingDto(99L, "PR-ABC123", BookingStatus.ASSIGNED, RideType.SHARED, 1, false,
                "A. Raghavan", "+91 98450 41227", null,
                new RouteRef(1L, "R1", "North corridor", "Route 1 · North corridor"),
                new StopRef(1L, "GATE2", "Gate 2"), new StopRef(4L, "BF", "Blast Furnace"),
                null, java.time.LocalDateTime.of(2026, 9, 22, 14, 5), null, null, null, null, null,
                "4826", "TOKEN123", "http://localhost:8080/t/TOKEN123", null, null, "T-0922-ABCD", 2, null, progress,
                "CC-4471", "Operations", new BigDecimal("3.40"), 15, null, null, null, null, List.of(), null, true, false);
    }
}
