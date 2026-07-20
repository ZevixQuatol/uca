package com.twlic.uca.base.registry;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class RoundRobinSelector {

    public RegisteredInstance select(List<RegisteredInstance> instances, AtomicLong cursor) {
        if (instances.isEmpty()) {
            throw new IllegalArgumentException("instances must not be empty");
        }
        List<RegisteredInstance> ordered = instances.stream()
                .sorted(Comparator.comparing(RegisteredInstance::instanceId))
                .toList();
        int index = Math.floorMod(cursor.getAndIncrement(), ordered.size());
        return ordered.get(index);
    }
}
