package com.twlic.uca.client.demo;

import java.time.Instant;

public record PingResponse(
        String applicationCode,
        String instanceId,
        String message,
        Instant timestamp) {
}
