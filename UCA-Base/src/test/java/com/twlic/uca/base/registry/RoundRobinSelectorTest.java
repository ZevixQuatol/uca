package com.twlic.uca.base.registry;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class RoundRobinSelectorTest {

    @Test
    void selectsInstancesInDeterministicOrderAndWrapsAround() {
        RoundRobinSelector selector = new RoundRobinSelector();
        AtomicLong cursor = new AtomicLong();
        List<RegisteredInstance> instances = List.of(instance("auth-2"), instance("auth-1"));

        assertThat(selector.select(instances, cursor).instanceId()).isEqualTo("auth-1");
        assertThat(selector.select(instances, cursor).instanceId()).isEqualTo("auth-2");
        assertThat(selector.select(instances, cursor).instanceId()).isEqualTo("auth-1");
    }

    private RegisteredInstance instance(String instanceId) {
        Instant now = Instant.parse("2026-07-13T00:00:00Z");
        return new RegisteredInstance(
                "auth",
                "认证模块",
                instanceId,
                URI.create("http://" + instanceId + ":8080"),
                "1.0.0",
                Map.of(),
                InstanceStatus.ONLINE,
                now,
                now,
                now);
    }
}
