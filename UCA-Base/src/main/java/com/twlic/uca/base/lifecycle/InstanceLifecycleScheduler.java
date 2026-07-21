package com.twlic.uca.base.lifecycle;

import com.twlic.uca.base.config.RegistryProperties;
import com.twlic.uca.base.registry.InstanceRegistry;
import org.springframework.scheduling.annotation.Scheduled;

public class InstanceLifecycleScheduler {

    private final InstanceRegistry registry;
    private final RegistryProperties properties;

    public InstanceLifecycleScheduler(InstanceRegistry registry, RegistryProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${uca.base.scan-interval:5s}")
    public void refreshStatuses() {
        registry.updateLifecycle(
                properties.getHeartbeatTimeout(),
                properties.getOfflineRetention());
    }
}
