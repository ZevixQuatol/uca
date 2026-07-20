package com.twlic.uca.base.registry;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

public record InstanceRegistration(
        String applicationCode,
        String applicationName,
        String instanceId,
        URI baseUrl,
        String version,
        Map<String, String> metadata) {

    public InstanceRegistration {
        Objects.requireNonNull(applicationCode, "applicationCode");
        Objects.requireNonNull(applicationName, "applicationName");
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(baseUrl, "baseUrl");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
