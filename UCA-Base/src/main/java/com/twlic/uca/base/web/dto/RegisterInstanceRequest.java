package com.twlic.uca.base.web.dto;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import com.twlic.uca.base.registry.InstanceRegistration;
import com.twlic.uca.base.web.RequestValidationException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterInstanceRequest(
        @NotBlank @Size(max = 100) String applicationName,
        @NotNull URI baseUrl,
        @Size(max = 50) String version,
        Map<String, String> metadata) {

    private static final Set<String> SUPPORTED_SCHEMES = Set.of("http", "https");

    public InstanceRegistration toRegistration(String applicationCode, String instanceId) {
        String scheme = baseUrl.getScheme();
        if (!baseUrl.isAbsolute()
                || scheme == null
                || !SUPPORTED_SCHEMES.contains(scheme.toLowerCase())
                || baseUrl.getHost() == null) {
            throw new RequestValidationException("baseUrl must be an absolute HTTP or HTTPS URL");
        }
        return new InstanceRegistration(
                applicationCode,
                applicationName,
                instanceId,
                baseUrl,
                version,
                metadata == null ? Map.of() : metadata);
    }
}
