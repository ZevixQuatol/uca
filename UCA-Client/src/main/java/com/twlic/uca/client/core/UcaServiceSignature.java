package com.twlic.uca.client.core;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

public final class UcaServiceSignature {

    public static final String CALLER_APPLICATION = "X-UCA-Caller-Application";
    public static final String CALLER_INSTANCE = "X-UCA-Caller-Instance";
    public static final String CALL_TYPE = "X-UCA-Call-Type";
    public static final String TIMESTAMP = "X-UCA-Timestamp";
    public static final String NONCE = "X-UCA-Nonce";
    public static final String BODY_SHA256 = "X-UCA-Body-SHA256";
    public static final String SIGNATURE = "X-UCA-Signature";
    public static final String REQUEST_ID = "X-Request-ID";

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final UcaClientProperties properties;
    private final Clock clock;
    private final Map<String, Long> seenNonces = new ConcurrentHashMap<>();

    public UcaServiceSignature(UcaClientProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public void sign(
            HttpHeaders headers,
            HttpMethod method,
            String rawPath,
            String rawQuery,
            byte[] body,
            String requestId) {

        requireSecret();
        String timestamp = Long.toString(clock.millis());
        String nonce = UUID.randomUUID().toString();
        String bodyHash = sha256(body);
        String instanceId = properties.requireInstanceId();

        headers.set(CALLER_APPLICATION, properties.getCode());
        headers.set(CALLER_INSTANCE, instanceId);
        headers.set(CALL_TYPE, "RELAY");
        headers.set(TIMESTAMP, timestamp);
        headers.set(NONCE, nonce);
        headers.set(BODY_SHA256, bodyHash);
        headers.set(REQUEST_ID, requestId);
        headers.set(SIGNATURE, hmac(canonical(
                method.name(),
                rawPath,
                rawQuery,
                properties.getCode(),
                instanceId,
                timestamp,
                nonce,
                bodyHash)));
    }

    public boolean isServiceCall(HttpServletRequest request) {
        return request.getHeader(SIGNATURE) != null;
    }

    public void verify(HttpServletRequest request, byte[] body) {
        requireSecret();
        String callerApplication = requiredHeader(request, CALLER_APPLICATION);
        String callerInstance = requiredHeader(request, CALLER_INSTANCE);
        String timestamp = requiredHeader(request, TIMESTAMP);
        String nonce = requiredHeader(request, NONCE);
        String bodyHash = requiredHeader(request, BODY_SHA256);
        String actualSignature = requiredHeader(request, SIGNATURE);

        long timestampMillis;
        try {
            timestampMillis = Long.parseLong(timestamp);
        } catch (NumberFormatException exception) {
            throw invalidSignature();
        }

        long now = clock.millis();
        long validityMillis = properties.getSignatureValidity().toMillis();
        if (Math.abs(now - timestampMillis) > validityMillis || !bodyHash.equals(sha256(body))) {
            throw invalidSignature();
        }

        String canonical = canonical(
                request.getMethod(),
                request.getRequestURI(),
                request.getQueryString(),
                callerApplication,
                callerInstance,
                timestamp,
                nonce,
                bodyHash);
        if (!matches(properties.getInternalSecret(), canonical, actualSignature)
                && !matches(properties.getPreviousInternalSecret(), canonical, actualSignature)) {
            throw invalidSignature();
        }

        long expiresAt = timestampMillis + validityMillis;
        seenNonces.entrySet().removeIf(entry -> entry.getValue() < now);
        if (seenNonces.putIfAbsent(callerApplication + ':' + callerInstance + ':' + nonce, expiresAt) != null) {
            throw invalidSignature();
        }
    }

    private void requireSecret() {
        if (properties.getInternalSecret().isBlank()) {
            throw new UcaException(UcaResponseCode.UCA_SERVICE_AUTH_REQUIRED);
        }
    }

    private String hmac(String value) {
        return hmac(properties.getInternalSecret(), value);
    }

    private String hmac(String secret, String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    HMAC_SHA256));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new UcaException(
                    UcaResponseCode.UCA_INTERNAL_ERROR,
                    "Unable to create UCA service signature",
                    exception);
        }
    }

    private boolean matches(String secret, String canonical, String actualSignature) {
        if (secret == null || secret.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                hmac(secret, canonical).getBytes(StandardCharsets.US_ASCII),
                actualSignature.getBytes(StandardCharsets.US_ASCII));
    }

    private static String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String canonical(
            String method,
            String rawPath,
            String rawQuery,
            String callerApplication,
            String callerInstance,
            String timestamp,
            String nonce,
            String bodyHash) {

        return String.join("\n",
                method,
                rawPath == null ? "" : rawPath,
                rawQuery == null ? "" : rawQuery,
                callerApplication,
                callerInstance,
                timestamp,
                nonce,
                bodyHash);
    }

    private static String requiredHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            throw invalidSignature();
        }
        return value;
    }

    private static UcaException invalidSignature() {
        return new UcaException(UcaResponseCode.UCA_SERVICE_SIGNATURE_INVALID);
    }
}
