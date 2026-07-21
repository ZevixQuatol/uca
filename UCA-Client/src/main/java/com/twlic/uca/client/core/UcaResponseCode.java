package com.twlic.uca.client.core;

public enum UcaResponseCode {

    SUCCESS(0, "SUCCESS"),
    UCA_INTERNAL_ERROR(10000, "UCA internal error"),
    UCA_INVALID_REQUEST(10001, "Invalid UCA request"),
    UCA_INVALID_SERVICE_NAME(10002, "Invalid service name"),
    UCA_INVALID_RELATIVE_PATH(10003, "Invalid relative path"),
    UCA_SERVICE_NOT_FOUND(10004, "Service not found"),
    UCA_SERVICE_OFFLINE(10005, "Service has no online instance"),
    UCA_TARGET_CONNECTION_FAILED(10006, "Unable to connect to target service"),
    UCA_TARGET_TIMEOUT(10007, "Target service call timed out"),
    UCA_ENDPOINT_NOT_EXPOSED(10008, "Target endpoint is not exposed to UCA services"),
    UCA_SERVICE_AUTH_REQUIRED(10009, "UCA service authentication is required"),
    UCA_SERVICE_SIGNATURE_INVALID(10010, "UCA service signature is invalid"),
    UCA_CALLER_NOT_ONLINE(10011, "Calling service instance is not online"),
    UCA_RESPONSE_INVALID(10012, "Target response does not follow the UCA protocol");

    private final int code;
    private final String defaultMessage;

    UcaResponseCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public int code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
