package com.twlic.uca.client.core;

import java.util.List;

public record UcaApplication(
        String applicationCode,
        String applicationName,
        List<UcaServiceInstance> instances) {

    public UcaApplication {
        instances = instances == null ? List.of() : List.copyOf(instances);
    }
}
