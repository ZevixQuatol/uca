package com.twlic.uca.base.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component("registryProperties")
@ConfigurationProperties(prefix = "uca.registry")
public class RegistryProperties {

    private Duration heartbeatTimeout = Duration.ofSeconds(30);
    private Duration offlineRetention = Duration.ofMinutes(2);
    private Duration scanInterval = Duration.ofSeconds(5);

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

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
