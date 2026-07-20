package com.twlic.uca.base.web.dto;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

import com.twlic.uca.base.registry.InstanceStatus;
import com.twlic.uca.base.registry.RegisteredInstance;

public record InstanceResponse(
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

    public static InstanceResponse from(RegisteredInstance instance) {
        return new InstanceResponse(
                instance.applicationCode(),
                instance.applicationName(),
                instance.instanceId(),
                instance.baseUrl(),
                instance.version(),
                instance.metadata(),
                instance.status(),
                instance.registeredAt(),
                instance.lastHeartbeatAt(),
                instance.statusChangedAt());
    }
}
