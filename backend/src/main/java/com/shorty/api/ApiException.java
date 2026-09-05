package com.shorty.api;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Integer retryAfterSeconds;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null, null);
    }

    public ApiException(HttpStatus status, String code, String message, Throwable cause) {
        this(status, code, message, cause, null);
    }

    public ApiException(HttpStatus status, String code, String message, Integer retryAfterSeconds) {
        this(status, code, message, null, retryAfterSeconds);
    }

    private ApiException(
            HttpStatus status, String code, String message, Throwable cause, Integer retryAfterSeconds) {
        super(message, cause);
        this.status = status;
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public Integer getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
