package com.srmecotech.plantride.common.error;

import org.springframework.http.HttpStatus;

/**
 * A well-formed request that a business rule refuses (cap reached, document
 * expired, wrong trip state...). Maps to 409 Conflict.
 */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
