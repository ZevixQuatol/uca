package com.twlic.uca.client.core;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/uca/client")
public class UcaClientEndpoint {

    private final UcaClient client;
    private final UcaClientProperties properties;
    private final RegistrationLifecycle lifecycle;

    public UcaClientEndpoint(
            UcaClient client,
            UcaClientProperties properties,
            RegistrationLifecycle lifecycle) {
        this.client = client;
        this.properties = properties;
        this.lifecycle = lifecycle;
    }

    @GetMapping
    public ClientStatus status() {
        return new ClientStatus(
                properties.getCode(),
                properties.getName(),
                properties.getInstanceId(),
                properties.getVersion(),
                properties.getAdvertisedBaseUrl(),
                properties.getMetadata(),
                lifecycle.isRegistered(),
                client.selfLoad());
    }

    @GetMapping("/services")
    public List<String> services() {
        return client.availableServiceNames();
    }

    public record ClientStatus(
            String applicationCode,
            String applicationName,
            String instanceId,
            String version,
            URI advertisedBaseUrl,
            Map<String, String> metadata,
            boolean registered,
            UcaSelfLoad load) {
    }
}
