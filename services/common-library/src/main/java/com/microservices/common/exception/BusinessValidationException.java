package com.microservices.common.exception;

import org.springframework.http.HttpStatus;

public class BusinessValidationException extends ServiceException {

    public BusinessValidationException(String message) {
        super(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }
}
