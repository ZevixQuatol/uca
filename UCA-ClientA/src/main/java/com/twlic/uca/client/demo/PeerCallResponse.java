package com.twlic.uca.client.demo;

import java.net.URI;

public record PeerCallResponse(
        String callerApplicationCode,
        String targetApplicationCode,
        String targetInstanceId,
        URI targetBaseUrl,
        PingResponse targetResponse) {
}
