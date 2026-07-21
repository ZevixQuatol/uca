package com.twlic.uca.client.core;

import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestClientException;

public class RegistrationLifecycle {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistrationLifecycle.class);

    private final UcaClient client;
    private final UcaClientProperties properties;
    private final AtomicBoolean registered = new AtomicBoolean();

    public RegistrationLifecycle(UcaClient client, UcaClientProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerOnStartup() {
        tryRegister();
        refreshDirectory();
    }

    @Scheduled(
            initialDelayString = "${uca.client.interval:5s}",
            fixedDelayString = "${uca.client.interval:5s}")
    public void maintainRegistration() {
        try {
            if (registered.get() && !client.heartbeat()) {
                registered.set(false);
            }
            if (!registered.get()) {
                tryRegister();
            }
        } catch (RestClientException exception) {
            LOGGER.warn("Unable to maintain registration in UCA-Base: {}", exception.getMessage());
        }
    }

    @Scheduled(
            initialDelayString = "${uca.client.interval:5s}",
            fixedDelayString = "${uca.client.interval:5s}")
    public void refreshDirectory() {
        try {
            client.refreshServices();
        } catch (RestClientException exception) {
            LOGGER.warn("Unable to refresh the local UCA service directory: {}", exception.getMessage());
        }
    }

    @PreDestroy
    public void deregisterOnShutdown() {
        if (!registered.get()) {
            return;
        }
        try {
            client.deregister();
            registered.set(false);
        } catch (RestClientException exception) {
            LOGGER.warn("Unable to deregister from UCA-Base during shutdown: {}", exception.getMessage());
        }
    }

    public boolean isRegistered() {
        return registered.get();
    }

    private synchronized void tryRegister() {
        if (registered.get()) {
            return;
        }
        try {
            client.register();
            registered.set(true);
            LOGGER.info("Registered {} instance {} in UCA-Base",
                    properties.getCode(), properties.getInstanceId());
        } catch (RestClientException | UcaException exception) {
            registered.set(false);
            LOGGER.warn("Unable to register in UCA-Base: {}", exception.getMessage());
        }
    }
}
