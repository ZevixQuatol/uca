package com.twlic.uca.client.integration;

import java.net.URI;
import java.util.Map;

public record BaseRegistrationRequest(
        String applicationName,
        URI baseUrl,
        String version,
        Map<String, String> metadata) {
}
