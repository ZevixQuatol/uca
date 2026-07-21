package com.twlic.uca.client.core;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

public record UcaServiceInstance(
        String applicationCode,
        String applicationName,
        String instanceId,
        URI baseUrl,
        String version,
        Map<String, String> metadata,
        String status,
        Instant registeredAt,
        Instant lastHeartbeatAt,
        Instant statusChangedAt) {

    public UcaServiceInstance {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
