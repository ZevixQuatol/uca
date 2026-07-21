package com.twlic.uca.base.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "uca.base")
public class RegistryProperties {

    private Duration heartbeatTimeout = Duration.ofSeconds(30);
    private Duration offlineRetention = Duration.ofMinutes(2);
    private Duration scanInterval = Duration.ofSeconds(5);
    private Duration secretInterval = Duration.ofMinutes(10);

    public Duration getHeartbeatTimeout() {
        return heartbeatTimeout;
    }

    public void setHeartbeatTimeout(Duration heartbeatTimeout) {
        this.heartbeatTimeout = requirePositive(heartbeatTimeout, "heartbeatTimeout");
    }

    public Duration getOfflineRetention() {
        return offlineRetention;
    }

    public void setOfflineRetention(Duration offlineRetention) {
        this.offlineRetention = requirePositive(offlineRetention, "offlineRetention");
    }

    public Duration getScanInterval() {
        return scanInterval;
    }

    public void setScanInterval(Duration scanInterval) {
        this.scanInterval = requirePositive(scanInterval, "scanInterval");
    }

    public Duration getSecretInterval() {
        return secretInterval;
    }

    public void setSecretInterval(Duration secretInterval) {
        this.secretInterval = requirePositive(secretInterval, "secretInterval");
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
