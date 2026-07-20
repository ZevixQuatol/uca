package com.twlic.uca.client.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${uca.client.api-prefix}/demo")
public class DemoController {

    private final PeerInvocationService service;

    public DemoController(PeerInvocationService service) {
        this.service = service;
    }

    @GetMapping("/ping")
    public PingResponse ping() {
        return service.ping();
    }

    @GetMapping("${uca.client.call-peer-path}")
    public PeerCallResponse callPeer() {
        return service.callPeer();
    }
}
