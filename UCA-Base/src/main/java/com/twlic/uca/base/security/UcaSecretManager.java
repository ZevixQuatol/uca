package com.twlic.uca.base.security;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.scheduling.annotation.Scheduled;

public class UcaSecretManager {

    public static final String SECRET_HEADER = "X-UCA-Registration-Secret";

    private final SecureRandom random = new SecureRandom();
    private final AtomicReference<String> secret = new AtomicReference<>(generate());

    public String current() {
        return secret.get();
    }

    @Scheduled(fixedDelayString = "${uca.base.secret-interval:10m}")
    public void rotate() {
        secret.set(generate());
    }

    private String generate() {
        byte[] value = new byte[32];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
