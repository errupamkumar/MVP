package com.srmecotech.ecoride.common.security;

import com.srmecotech.ecoride.common.error.ApiException;
import org.springframework.http.HttpStatus;

public final class AuthErrors {

    private AuthErrors() {
    }

    public static ApiException badCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS",
                "That phone number or email and password do not match an account.");
    }

    public static ApiException blocked(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_BLOCKED", message);
    }
}
