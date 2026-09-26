package com.meterflow.common.error;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(MeterFlowException.class)
    public ResponseEntity<ProblemDetail> handleMeterFlowException(
            MeterFlowException ex) {

        log.warn(
                "MeterFlowException: errorCode={}, message={}",
                ex.getErrorCode().name(),
                ex.getMessage()
        );

        ProblemDetail problemDetail = problemDetail(
                ex.getErrorCode().getStatus(),
                ex.getMessage(),
                ex.getErrorCode().name()
        );

        return ResponseEntity
                .status(ex.getErrorCode().getStatus())
                .body(problemDetail);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneral(Exception ex) {

        String correlationId = UUID.randomUUID().toString();

        log.error(
                "Unexpected error. correlationId={}",
                correlationId,
                ex
        );

        ProblemDetail problemDetail = problemDetail(ErrorCode.INTERNAL_ERROR.getStatus(), "An unexpected error occurred" , ErrorCode.INTERNAL_ERROR.name());

        problemDetail.setProperty(
                "correlationId",
                correlationId
        );

        return ResponseEntity
                .status(ErrorCode.INTERNAL_ERROR.getStatus())
                .body(problemDetail);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {

        ProblemDetail problemDetail;

        if (body instanceof ProblemDetail existing) {
            problemDetail = existing;
        } else {
            problemDetail = ProblemDetail.forStatus(statusCode);
        }

        problemDetail.setProperty(
                "errorCode",
                resolveErrorCode(ex, statusCode)
        );

        problemDetail.setProperty(
                "timestamp",
                Instant.now(clock)
        );

        return super.handleExceptionInternal(
                ex,
                problemDetail,
                headers,
                statusCode,
                request
        );
    }

    private String resolveErrorCode(
            Exception ex,
            HttpStatusCode statusCode) {

        if (statusCode.value() == HttpStatus.NOT_FOUND.value()) {
            return "RESOURCE_NOT_FOUND";
        }

        if (statusCode.value() == HttpStatus.METHOD_NOT_ALLOWED.value()) {
            return "METHOD_NOT_ALLOWED";
        }

        if (statusCode.value() == HttpStatus.BAD_REQUEST.value()) {
            return "MALFORMED_REQUEST";
        }

        return "HTTP_" + statusCode.value();
    }

    private ProblemDetail problemDetail(
            HttpStatusCode status,
            String detail,
            String errorCode) {

        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(status, detail);

        problemDetail.setProperty("errorCode", errorCode);
        problemDetail.setProperty("timestamp", Instant.now(clock));

        return problemDetail;
    }
}