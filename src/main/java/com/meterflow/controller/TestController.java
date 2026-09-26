package com.meterflow.controller;

import com.meterflow.common.error.ErrorCode;
import com.meterflow.common.error.MeterFlowException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v0")
public class TestController {

    @GetMapping("/test")
    public void testException(){
        throw new MeterFlowException(
                ErrorCode.CUSTOMER_NOT_FOUND,
                "Customer does not exist"
        );
    }

    @GetMapping("/test-internal")
    public void testInternalException() {
        throw new RuntimeException("Something went wrong");
    }
}
