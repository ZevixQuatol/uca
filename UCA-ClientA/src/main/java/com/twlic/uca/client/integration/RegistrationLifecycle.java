package com.twlic.uca.client.integration;

import java.util.concurrent.atomic.AtomicBoolean;

import com.twlic.uca.client.config.ChildSystemProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@Component
public class RegistrationLifecycle {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistrationLifecycle.class);

    private final UcaBaseClient baseClient;
    private final ChildSystemProperties properties;
    private final AtomicBoolean registered = new AtomicBoolean();

    public RegistrationLifecycle(UcaBaseClient baseClient, ChildSystemProperties properties) {
        this.baseClient = baseClient;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerOnStartup() {
        tryRegister();
    }

    @Scheduled(
            initialDelayString = "#{@childSystemProperties.heartbeatInterval.toMillis()}",
            fixedDelayString = "#{@childSystemProperties.heartbeatInterval.toMillis()}")
    public void maintainRegistration() {
        try {
            if (!registered.get() || !baseClient.heartbeat()) {
                tryRegister();
            }
        } catch (RestClientException exception) {
            registered.set(false);
            LOGGER.warn("Unable to maintain registration in UCA-Base: {}", exception.getMessage());
        }
    }

    @PreDestroy
    public void deregisterOnShutdown() {
        if (!registered.get()) {
            return;
        }
        try {
            baseClient.deregister();
        } catch (RestClientException exception) {
            LOGGER.warn("Unable to deregister from UCA-Base during shutdown: {}", exception.getMessage());
        }
    }

    public boolean isRegistered() {
        return registered.get();
    }

    private void tryRegister() {
        try {
            baseClient.register();
            registered.set(true);
            LOGGER.info("Registered {} instance {} in UCA-Base",
                    properties.getApplicationCode(), properties.getInstanceId());
        } catch (RestClientException exception) {
            registered.set(false);
            LOGGER.warn("Unable to register in UCA-Base: {}", exception.getMessage());
        }
    }
}
