package com.twlic.uca.base.registry;

import java.util.List;

public record ApplicationSnapshot(
        String applicationCode,
        String applicationName,
        List<RegisteredInstance> instances) {

    public ApplicationSnapshot {
        instances = List.copyOf(instances);
    }
}
