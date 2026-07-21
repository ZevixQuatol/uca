package com.twlic.uca.client.core;

public record UcaResult<T>(
        int code,
        String error,
        String message,
        T data,
        String requestId) {

    public static <T> UcaResult<T> success(T data, String requestId) {
        return new UcaResult<>(0, null, "SUCCESS", data, requestId);
    }

    public static UcaResult<Void> failure(UcaException exception, String requestId) {
        return new UcaResult<>(
                exception.code(),
                exception.error(),
                exception.getMessage(),
                null,
                requestId);
    }
}
