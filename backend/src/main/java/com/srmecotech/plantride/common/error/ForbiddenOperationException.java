package com.srmecotech.plantride.common.error;

import org.springframework.http.HttpStatus;

/** The caller is authenticated but does not own the resource it is acting on. */
public class ForbiddenOperationException extends ApiException {

    public ForbiddenOperationException(String message) {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }
}
