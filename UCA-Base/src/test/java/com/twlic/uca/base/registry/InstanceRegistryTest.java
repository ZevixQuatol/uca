package com.twlic.uca.base.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import com.twlic.uca.base.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InstanceRegistryTest {

    private MutableClock clock;
    private InstanceRegistry registry;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-07-13T00:00:00Z"));
        registry = new InstanceRegistry(clock);
    }

    @Test
    void registersANewInstance() {
        RegistrationResult result = registry.register(registration(
                "auth", "认证模块", "auth-1", "http://auth-1:8080", "1.0.0"));

        assertThat(result.created()).isTrue();
        assertThat(result.instance())
                .extracting(
                        RegisteredInstance::applicationCode,
                        RegisteredInstance::applicationName,
                        RegisteredInstance::instanceId,
                        RegisteredInstance::baseUrl,
                        RegisteredInstance::version,
                        RegisteredInstance::status,
                        RegisteredInstance::registeredAt,
                        RegisteredInstance::lastHeartbeatAt)
                .containsExactly(
                        "auth",
                        "认证模块",
                        "auth-1",
                        URI.create("http://auth-1:8080"),
                        "1.0.0",
                        InstanceStatus.ONLINE,
                        clock.instant(),
                        clock.instant());
        assertThat(result.instance().metadata()).containsEntry("zone", "default");
    }

    @Test
    void updatesDuplicateRegistrationAndPreservesFirstRegistrationTime() {
        Instant firstRegistration = clock.instant();
        registry.register(registration(
                "auth", "认证模块", "auth-1", "http://auth-1:8080", "1.0.0"));
        clock.advance(Duration.ofSeconds(5));

        RegistrationResult result = registry.register(new InstanceRegistration(
                "auth",
                "统一认证",
                "auth-1",
                URI.create("https://auth-1:8443"),
                "1.1.0",
                Map.of("zone", "secondary")));

        assertThat(result.created()).isFalse();
        assertThat(result.instance().registeredAt()).isEqualTo(firstRegistration);
        assertThat(result.instance().lastHeartbeatAt()).isEqualTo(clock.instant());
        assertThat(result.instance().applicationName()).isEqualTo("统一认证");
        assertThat(result.instance().baseUrl()).isEqualTo(URI.create("https://auth-1:8443"));
        assertThat(result.instance().version()).isEqualTo("1.1.0");
        assertThat(result.instance().metadata()).containsExactlyEntriesOf(Map.of("zone", "secondary"));
    }

    @Test
    void keepsMultipleInstancesUnderOneApplication() {
        registry.register(registration(
                "auth", "认证模块", "auth-2", "http://auth-2:8080", "1.0.0"));
        registry.register(registration(
                "auth", "认证模块", "auth-1", "http://auth-1:8080", "1.0.0"));

        ApplicationSnapshot application = registry.findApplication("auth");

        assertThat(application.instances())
                .extracting(RegisteredInstance::instanceId)
                .containsExactly("auth-1", "auth-2");
    }

    @Test
    void isolatesApplicationsAndReturnsSortedSnapshots() {
        registry.register(registration(
                "quality", "质量评查", "quality-1", "http://quality-1:8080", "2.0.0"));
        registry.register(registration(
                "auth", "认证模块", "auth-1", "http://auth-1:8080", "1.0.0"));

        assertThat(registry.findAll())
                .extracting(ApplicationSnapshot::applicationCode)
                .containsExactly("auth", "quality");
        assertThat(registry.findApplication("auth").instances())
                .extracting(RegisteredInstance::instanceId)
                .containsExactly("auth-1");
        assertThat(registry.findApplication("quality").instances())
                .extracting(RegisteredInstance::instanceId)
                .containsExactly("quality-1");
    }

    @Test
    void heartbeatRenewsAKnownInstance() {
        registry.register(registration(
                "auth", "认证模块", "auth-1", "http://auth-1:8080", "1.0.0"));
        clock.advance(Duration.ofSeconds(10));

        RegisteredInstance instance = registry.heartbeat("auth", "auth-1");

        assertThat(instance.status()).isEqualTo(InstanceStatus.ONLINE);
        assertThat(instance.lastHeartbeatAt()).isEqualTo(clock.instant());
    }

    @Test
    void heartbeatRejectsAnUnknownInstance() {
        assertThatThrownBy(() -> registry.heartbeat("auth", "missing"))
                .isInstanceOfSatisfying(RegistryException.class, exception ->
                        assertThat(exception.error()).isEqualTo(RegistryError.INSTANCE_NOT_FOUND));
    }

    @Test
    void marksInstanceOfflineAtHeartbeatTimeoutBoundary() {
        registry.register(registration(
                "auth", "认证模块", "auth-1", "http://auth-1:8080", "1.0.0"));
        clock.advance(Duration.ofSeconds(30));

        registry.updateLifecycle(Duration.ofSeconds(30), Duration.ofMinutes(2));

        RegisteredInstance instance = registry.findApplication("auth").instances().getFirst();
        assertThat(instance.status()).isEqualTo(InstanceStatus.OFFLINE);
        assertThat(instance.statusChangedAt()).isEqualTo(clock.instant());
    }

    @Test
    void removesOfflineInstanceAtRetentionBoundary() {
        registry.register(registration(
                "auth", "认证模块", "auth-1", "http://auth-1:8080", "1.0.0"));
        clock.advance(Duration.ofSeconds(30));
        registry.updateLifecycle(Duration.ofSeconds(30), Duration.ofMinutes(2));
        clock.advance(Duration.ofMinutes(2));

        registry.updateLifecycle(Duration.ofSeconds(30), Duration.ofMinutes(2));

        assertThat(registry.findAll()).isEmpty();
    }

    @Test
    void reRegistrationRecoversAnOfflineInstance() {
        registry.register(registration(
                "auth", "认证模块", "auth-1", "http://auth-1:8080", "1.0.0"));
        clock.advance(Duration.ofSeconds(30));
        registry.updateLifecycle(Duration.ofSeconds(30), Duration.ofMinutes(2));
        clock.advance(Duration.ofSeconds(1));

        RegistrationResult result = registry.register(registration(
                "auth", "认证模块", "auth-1", "http://auth-1:8080", "1.0.0"));

        assertThat(result.created()).isFalse();
        assertThat(result.instance().status()).isEqualTo(InstanceStatus.ONLINE);
        assertThat(result.instance().statusChangedAt()).isEqualTo(clock.instant());
        assertThat(result.instance().lastHeartbeatAt()).isEqualTo(clock.instant());
    }

    @Test
    void resumedHeartbeatRecoversAnOfflineInstance() {
        registry.register(registration(
                "auth", "认证模块", "auth-1", "http://auth-1:8080", "1.0.0"));
        clock.advance(Duration.ofSeconds(30));
        registry.updateLifecycle(Duration.ofSeconds(30), Duration.ofMinutes(2));
        clock.advance(Duration.ofSeconds(1));

        RegisteredInstance instance = registry.heartbeat("auth", "auth-1");

        assertThat(instance.status()).isEqualTo(InstanceStatus.ONLINE);
        assertThat(instance.statusChangedAt()).isEqualTo(clock.instant());
        assertThat(instance.lastHeartbeatAt()).isEqualTo(clock.instant());
    }

    @Test
    void deregistrationIsIdempotentAndRemovesEmptyApplication() {
        registry.register(registration(
                "auth", "认证模块", "auth-1", "http://auth-1:8080", "1.0.0"));

        registry.deregister("auth", "auth-1");
        registry.deregister("auth", "auth-1");

        assertThat(registry.findAll()).isEmpty();
    }

    @Test
    void discoversOnlineInstancesWithApplicationScopedRoundRobin() {
        registry.register(registration(
                "auth", "认证模块", "auth-2", "http://auth-2:8080", "1.0.0"));
        registry.register(registration(
                "auth", "认证模块", "auth-1", "http://auth-1:8080", "1.0.0"));

        assertThat(registry.discover("auth").instanceId()).isEqualTo("auth-1");
        assertThat(registry.discover("auth").instanceId()).isEqualTo("auth-2");
        assertThat(registry.discover("auth").instanceId()).isEqualTo("auth-1");
    }

    @Test
    void excludesOfflineInstancesFromDiscovery() {
        registry.register(registration(
                "auth", "认证模块", "auth-1", "http://auth-1:8080", "1.0.0"));
        registry.register(registration(
                "auth", "认证模块", "auth-2", "http://auth-2:8080", "1.0.0"));
        clock.advance(Duration.ofSeconds(20));
        registry.heartbeat("auth", "auth-2");
        clock.advance(Duration.ofSeconds(10));
        registry.updateLifecycle(Duration.ofSeconds(30), Duration.ofMinutes(2));

        assertThat(registry.discover("auth").instanceId()).isEqualTo("auth-2");
    }

    @Test
    void keepsRoundRobinCursorsIndependentAcrossApplications() {
        registry.register(registration(
                "auth", "认证模块", "auth-1", "http://auth-1:8080", "1.0.0"));
        registry.register(registration(
                "auth", "认证模块", "auth-2", "http://auth-2:8080", "1.0.0"));
        registry.register(registration(
                "quality", "质量评查", "quality-1", "http://quality-1:8080", "1.0.0"));
        registry.register(registration(
                "quality", "质量评查", "quality-2", "http://quality-2:8080", "1.0.0"));

        assertThat(registry.discover("auth").instanceId()).isEqualTo("auth-1");
        assertThat(registry.discover("auth").instanceId()).isEqualTo("auth-2");
        assertThat(registry.discover("quality").instanceId()).isEqualTo("quality-1");
    }

    @Test
    void discoveryRejectsAnUnknownApplication() {
        assertThatThrownBy(() -> registry.discover("missing"))
                .isInstanceOfSatisfying(RegistryException.class, exception ->
                        assertThat(exception.error()).isEqualTo(RegistryError.APPLICATION_NOT_FOUND));
    }

    @Test
    void discoveryRejectsApplicationWithoutOnlineInstances() {
        registry.register(registration(
                "auth", "认证模块", "auth-1", "http://auth-1:8080", "1.0.0"));
        clock.advance(Duration.ofSeconds(30));
        registry.updateLifecycle(Duration.ofSeconds(30), Duration.ofMinutes(2));

        assertThatThrownBy(() -> registry.discover("auth"))
                .isInstanceOfSatisfying(RegistryException.class, exception ->
                        assertThat(exception.error()).isEqualTo(RegistryError.NO_ONLINE_INSTANCE));
    }

    private InstanceRegistration registration(
            String applicationCode,
            String applicationName,
            String instanceId,
            String baseUrl,
            String version) {
        return new InstanceRegistration(
                applicationCode,
                applicationName,
                instanceId,
                URI.create(baseUrl),
                version,
                Map.of("zone", "default"));
    }
}
