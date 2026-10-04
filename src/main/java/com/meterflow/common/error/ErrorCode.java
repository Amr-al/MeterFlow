package com.meterflow.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND),
    PLAN_NOT_FOUND(HttpStatus.NOT_FOUND),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND),
    SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND),

    DUPLICATE_RESOURCE(HttpStatus.CONFLICT),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT),
    SUBSCRIPTION_ALREADY_ACTIVE(HttpStatus.CONFLICT),

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST),
    CUSTOMER_NOT_ACTIVE(HttpStatus.BAD_REQUEST),
    PLAN_NOT_ACTIVE(HttpStatus.BAD_REQUEST),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}