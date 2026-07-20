package com.twlic.uca.base.registry;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

public record RegisteredInstance(
        String applicationCode,
        String applicationName,
        String instanceId,
        URI baseUrl,
        String version,
        Map<String, String> metadata,
        InstanceStatus status,
        Instant registeredAt,
        Instant lastHeartbeatAt,
        Instant statusChangedAt) {

    public RegisteredInstance {
        metadata = Map.copyOf(metadata);
    }

    static RegisteredInstance firstRegistration(InstanceRegistration registration, Instant now) {
        return new RegisteredInstance(
                registration.applicationCode(),
                registration.applicationName(),
                registration.instanceId(),
                registration.baseUrl(),
                registration.version(),
                registration.metadata(),
                InstanceStatus.ONLINE,
                now,
                now,
                now);
    }

    RegisteredInstance reRegister(InstanceRegistration registration, Instant now) {
        Instant nextStatusChangedAt = status == InstanceStatus.ONLINE ? statusChangedAt : now;
        return new RegisteredInstance(
                applicationCode,
                registration.applicationName(),
                instanceId,
                registration.baseUrl(),
                registration.version(),
                registration.metadata(),
                InstanceStatus.ONLINE,
                registeredAt,
                now,
                nextStatusChangedAt);
    }

    RegisteredInstance withApplicationName(String applicationName) {
        return new RegisteredInstance(
                applicationCode,
                applicationName,
                instanceId,
                baseUrl,
                version,
                metadata,
                status,
                registeredAt,
                lastHeartbeatAt,
                statusChangedAt);
    }

    RegisteredInstance heartbeat(Instant now) {
        Instant nextStatusChangedAt = status == InstanceStatus.ONLINE ? statusChangedAt : now;
        return new RegisteredInstance(
                applicationCode,
                applicationName,
                instanceId,
                baseUrl,
                version,
                metadata,
                InstanceStatus.ONLINE,
                registeredAt,
                now,
                nextStatusChangedAt);
    }

    RegisteredInstance markOffline(Instant now) {
        if (status == InstanceStatus.OFFLINE) {
            return this;
        }
        return new RegisteredInstance(
                applicationCode,
                applicationName,
                instanceId,
                baseUrl,
                version,
                metadata,
                InstanceStatus.OFFLINE,
                registeredAt,
                lastHeartbeatAt,
                now);
    }
}
