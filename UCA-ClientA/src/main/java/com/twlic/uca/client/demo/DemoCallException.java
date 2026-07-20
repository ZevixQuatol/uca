package com.twlic.uca.client.demo;

import org.springframework.http.HttpStatus;

public final class DemoCallException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public DemoCallException(String code, HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
