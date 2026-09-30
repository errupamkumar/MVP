package com.srmecotech.plantride.common.error;

import org.springframework.http.HttpStatus;

/**
 * Syntactically valid input that makes no sense for the domain (stops not on
 * any route, a pickup time in the past...). Maps to 422 Unprocessable Entity.
 */
public class InvalidRequestException extends ApiException {

    public InvalidRequestException(String code, String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }
}
