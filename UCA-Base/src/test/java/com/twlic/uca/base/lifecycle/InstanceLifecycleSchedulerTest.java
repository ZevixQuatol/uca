package com.twlic.uca.base.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import com.twlic.uca.base.config.RegistryProperties;
import com.twlic.uca.base.registry.InstanceRegistration;
import com.twlic.uca.base.registry.InstanceRegistry;
import com.twlic.uca.base.registry.InstanceStatus;
import com.twlic.uca.base.support.MutableClock;
import org.junit.jupiter.api.Test;

class InstanceLifecycleSchedulerTest {

    @Test
    void appliesConfiguredLifecycleThresholds() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-13T00:00:00Z"));
        InstanceRegistry registry = new InstanceRegistry(clock);
        RegistryProperties properties = new RegistryProperties();
        properties.setHeartbeatTimeout(Duration.ofSeconds(15));
        properties.setOfflineRetention(Duration.ofMinutes(1));
        InstanceLifecycleScheduler scheduler = new InstanceLifecycleScheduler(registry, properties);
        registry.register(new InstanceRegistration(
                "auth",
                "认证模块",
                "auth-1",
                URI.create("http://auth-1:8080"),
                "1.0.0",
                Map.of()));
        clock.advance(Duration.ofSeconds(15));

        scheduler.refreshStatuses();

        assertThat(registry.findApplication("auth").instances().getFirst().status())
                .isEqualTo(InstanceStatus.OFFLINE);
    }
}
