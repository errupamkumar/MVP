package com.srmecotech.plantride.common.error;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {

    public NotFoundException(String entity, Object id) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", entity + " " + id + " was not found");
    }

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
