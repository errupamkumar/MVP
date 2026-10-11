package com.srmecotech.ecoride.common.error;

import org.springframework.http.HttpStatus;

/**
 * Syntactically valid input that makes no sense for the domain (a pickup outside
 * the service area, a wrong OTP...). Maps to 422 Unprocessable Entity.
 */
public class InvalidRequestException extends ApiException {

    public InvalidRequestException(String code, String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }
}
