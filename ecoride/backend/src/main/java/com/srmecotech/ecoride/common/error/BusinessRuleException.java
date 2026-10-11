package com.srmecotech.ecoride.common.error;

import org.springframework.http.HttpStatus;

/**
 * A well-formed request that a business rule refuses (ride already taken, driver offline,
 * wrong ride state...). Maps to 409 Conflict.
 */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
