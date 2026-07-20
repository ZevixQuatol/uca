package com.twlic.uca.client.integration;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

public record BaseInstanceResponse(
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
}
