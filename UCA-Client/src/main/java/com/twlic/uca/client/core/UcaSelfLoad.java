package com.twlic.uca.client.core;

import java.time.Instant;

public record UcaSelfLoad(
        Instant sampledAt,
        double processCpuLoad,
        long heapUsedBytes,
        long heapMaxBytes,
        int liveThreadCount,
        int availableProcessors) {
}
