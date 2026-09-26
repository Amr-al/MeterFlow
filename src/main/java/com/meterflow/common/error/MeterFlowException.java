package com.meterflow.common.error;

import org.springframework.http.HttpStatus;

public class MeterFlowException extends RuntimeException{

    private final ErrorCode errorCode;

    public MeterFlowException(ErrorCode errorCode, String message){
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }


}
