package com.twlic.uca.client.core;

public final class UcaException extends RuntimeException {

    private final int code;
    private final String error;

    public UcaException(UcaResponseCode responseCode) {
        this(responseCode, responseCode.defaultMessage());
    }

    public UcaException(UcaResponseCode responseCode, String message) {
        this(responseCode.code(), responseCode.name(), message, null);
    }

    public UcaException(UcaResponseCode responseCode, String message, Throwable cause) {
        this(responseCode.code(), responseCode.name(), message, cause);
    }

    public UcaException(int code, String error, String message) {
        this(code, error, message, null);
    }

    private UcaException(int code, String error, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.error = error;
    }

    public int code() {
        return code;
    }

    public String error() {
        return error;
    }
}
