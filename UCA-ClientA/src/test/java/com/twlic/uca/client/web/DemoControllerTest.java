package com.twlic.uca.client.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.twlic.uca.client.config.ChildSystemProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "uca.client.base-registry-url=http://127.0.0.1:9",
        "uca.client.heartbeat-interval=1h"
})
@AutoConfigureMockMvc
class DemoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChildSystemProperties properties;

    @Test
    void exposesTheConfiguredPingEndpoint() throws Exception {
        mockMvc.perform(get(properties.getApiPrefix() + "/demo/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationCode").value("client-a"))
                .andExpect(jsonPath("$.instanceId").value("client-a-1"))
                .andExpect(jsonPath("$.message").value("pong from client-a"));
    }

    @Test
    void mapsBaseConnectionFailureToServiceUnavailable() throws Exception {
        mockMvc.perform(get(properties.getApiPrefix() + "/demo" + properties.getCallPeerPath()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UCA_BASE_UNAVAILABLE"));
    }

    @Test
    void exposesActuatorHealth() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
