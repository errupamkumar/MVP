package com.srmecotech.plantride.common.error;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The single error envelope every failing endpoint returns.
 *
 * @param code        stable machine-readable code (e.g. CAPACITY_REACHED, VALIDATION_FAILED)
 * @param message     human-readable, safe to show to the user
 * @param fieldErrors per-field validation messages, empty unless code = VALIDATION_FAILED
 */
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {
    }
}
