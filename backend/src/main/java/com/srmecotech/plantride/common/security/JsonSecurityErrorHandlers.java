package com.srmecotech.plantride.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.srmecotech.plantride.common.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Security failures happen before a controller is reached, so the
 * {@code @RestControllerAdvice} never sees them. These handlers write the
 * same {@link ErrorResponse} envelope so the client parses one shape.
 */
@Component
public class JsonSecurityErrorHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public JsonSecurityErrorHandlers(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        write(response, request, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                "Your session has expired or is missing. Please sign in again.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        write(response, request, HttpStatus.FORBIDDEN, "FORBIDDEN", "Your role does not have access to this action.");
    }

    private void write(HttpServletResponse response, HttpServletRequest request, HttpStatus status,
                       String code, String message) throws IOException {
        ErrorResponse body = new ErrorResponse(LocalDateTime.now(clock), status.value(), status.getReasonPhrase(),
                code, message, request.getRequestURI(), List.of());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
