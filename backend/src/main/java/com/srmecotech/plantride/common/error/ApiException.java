package com.srmecotech.plantride.common.error;

import org.springframework.http.HttpStatus;

/**
 * Base for every error the API raises on purpose. {@code code} is a stable,
 * machine-readable identifier the mobile app switches on; {@code message}
 * is written to be shown to the user as-is.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
