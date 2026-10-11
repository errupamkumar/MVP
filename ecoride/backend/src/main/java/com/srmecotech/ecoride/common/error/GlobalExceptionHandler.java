package com.srmecotech.ecoride.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Database constraint names mapped to messages a person can act on. Services check the
     * same rules first, so reaching one of these usually means two requests raced.
     */
    private static final Map<String, String> CONSTRAINT_MESSAGES = Map.ofEntries(
            Map.entry("uk_rider_phone", "An account with this phone number already exists. Sign in instead."),
            Map.entry("uk_rider_email", "An account with this email already exists. Sign in instead."),
            Map.entry("uk_driver_phone", "A driver account with this phone number already exists. Sign in instead."),
            Map.entry("uk_driver_email", "A driver account with this email already exists. Sign in instead."),
            Map.entry("uk_driver_plate", "This vehicle number is already registered with another driver."),
            Map.entry("uk_ride_active_rider", "You already have a ride in progress."),
            Map.entry("uk_ride_active_driver", "You already have an assigned ride."),
            Map.entry("uk_offer_pending_ride", "This ride is already being offered to a driver."),
            Map.entry("ck_driver_online_approved", "Only an approved driver can go online."),
            Map.entry("ck_vt_", "Fares, waiting charges and capacity must be zero or more (capacity 1 to 8)."),
            Map.entry("ck_surge_", "Surge must be between 1.00x and 3.00x, radius between 300 m and 20 km."),
            Map.entry("ck_promo_kind", "A promo needs a percentage (1-100) or a flat amount."));

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest request) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorResponse.FieldError> all = new ArrayList<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> all.add(new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage())));
        ex.getBindingResult().getGlobalErrors()
                .forEach(ge -> all.add(new ErrorResponse.FieldError(ge.getObjectName(), ge.getDefaultMessage())));
        String message = all.isEmpty() ? "The request is not valid." : all.get(0).field() + ": " + all.get(0).message();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, request, all);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        List<ErrorResponse.FieldError> fields = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(e -> new ErrorResponse.FieldError(r.getMethodParameter().getParameterName(), e.getDefaultMessage())))
                .toList();
        String message = fields.isEmpty() ? "The request is not valid." : fields.get(0).field() + ": " + fields.get(0).message();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, request, fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<ErrorResponse.FieldError> fields = ex.getConstraintViolations().stream()
                .map(v -> new ErrorResponse.FieldError(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        String message = fields.isEmpty() ? "The request is not valid." : fields.get(0).field() + ": " + fields.get(0).message();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, request, fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "The request body is missing or is not valid JSON for this endpoint.", request, List.of());
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class})
    public ResponseEntity<ErrorResponse> handleBadParameter(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "BAD_PARAMETER", ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have access to this action.", request, List.of());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "STALE_DATA",
                "Someone else changed this record a moment ago. Refresh and try again.", request, List.of());
    }

    @ExceptionHandler({CannotAcquireLockException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<ErrorResponse> handleLockFailure(Exception ex, HttpServletRequest request) {
        log.warn("Lock contention on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, "BUSY", "The ride is being updated right now. Try again in a moment.",
                request, List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        String detail = ex.getMostSpecificCause().getMessage();
        String lower = detail == null ? "" : detail.toLowerCase();
        String message = CONSTRAINT_MESSAGES.entrySet().stream()
                .filter(e -> lower.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("The request conflicts with existing data.");
        log.info("Integrity violation on {}: {}", request.getRequestURI(), detail);
        return build(HttpStatus.CONFLICT, "DATA_CONFLICT", message, request, List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "No endpoint at " + request.getRequestURI(), request, List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex,
                                                                   HttpServletRequest request) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "Send the request body as application/json.", request, List.of());
    }

    /** The client refuses every type we can produce, so no body can be written: status only. */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Void> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Something went wrong on our side. Please try again.", request, List.of());
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message,
                                                HttpServletRequest request, List<ErrorResponse.FieldError> fields) {
        ErrorResponse body = new ErrorResponse(clock.instant(), status.value(), status.getReasonPhrase(),
                code, message, request.getRequestURI(), fields);
        return ResponseEntity.status(status).body(body);
    }
}
