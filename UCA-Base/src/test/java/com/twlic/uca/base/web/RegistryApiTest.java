package com.twlic.uca.base.web;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import com.twlic.uca.base.registry.InstanceRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "uca.registry.heartbeat-timeout=1h",
        "uca.registry.offline-retention=1h"
})
@AutoConfigureMockMvc
class RegistryApiTest {

    private static final String REGISTER_BODY = """
            {
              "applicationName": "认证模块",
              "baseUrl": "http://auth-1:8080",
              "version": "1.0.0",
              "metadata": {"zone": "default"}
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InstanceRegistry registry;

    @Test
    void registrationReturnsCreatedThenOkForAnUpdate() throws Exception {
        String path = "/api/v1/applications/auth-api-registration/instances/auth-1";

        mockMvc.perform(put(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.applicationCode").value("auth-api-registration"))
                .andExpect(jsonPath("$.instanceId").value("auth-1"))
                .andExpect(jsonPath("$.status").value("ONLINE"));

        mockMvc.perform(put(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void heartbeatRenewsAKnownInstance() throws Exception {
        String path = "/api/v1/applications/auth-api-heartbeat/instances/auth-1";
        register(path);

        mockMvc.perform(put(path + "/heartbeat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ONLINE"));
    }

    @Test
    void heartbeatReturnsStableErrorForUnknownInstance() throws Exception {
        mockMvc.perform(put("/api/v1/applications/auth-api-missing/instances/missing/heartbeat"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INSTANCE_NOT_FOUND"));
    }

    @Test
    void deregistrationIsIdempotent() throws Exception {
        String path = "/api/v1/applications/auth-api-delete/instances/auth-1";
        register(path);

        mockMvc.perform(delete(path)).andExpect(status().isNoContent());
        mockMvc.perform(delete(path)).andExpect(status().isNoContent());
    }

    @Test
    void discoveryReturnsRoundRobinInstances() throws Exception {
        String first = "/api/v1/applications/auth-api-discovery/instances/auth-1";
        String second = "/api/v1/applications/auth-api-discovery/instances/auth-2";
        register(first);
        register(second, REGISTER_BODY.replace("auth-1:8080", "auth-2:8080"));

        mockMvc.perform(get("/api/v1/applications/auth-api-discovery/instances/next"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceId").value("auth-1"));
        mockMvc.perform(get("/api/v1/applications/auth-api-discovery/instances/next"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceId").value("auth-2"));
    }

    @Test
    void discoveryDistinguishesUnknownApplicationFromNoOnlineInstance() throws Exception {
        mockMvc.perform(get("/api/v1/applications/no-such-application/instances/next"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));

        String path = "/api/v1/applications/auth-api-offline/instances/auth-1";
        register(path);
        registry.updateLifecycle(Duration.ZERO, Duration.ofHours(1));

        mockMvc.perform(get("/api/v1/applications/auth-api-offline/instances/next"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("NO_ONLINE_INSTANCE"));
    }

    @Test
    void statusEndpointsReturnApplicationSnapshots() throws Exception {
        String path = "/api/v1/applications/auth-api-status/instances/auth-1";
        register(path);

        mockMvc.perform(get("/api/v1/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].applicationCode", hasItem("auth-api-status")));
        mockMvc.perform(get("/api/v1/applications/auth-api-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationCode").value("auth-api-status"))
                .andExpect(jsonPath("$.instances[0].instanceId").value("auth-1"));
    }

    @Test
    void statusReturnsNotFoundForUnknownApplication() throws Exception {
        mockMvc.perform(get("/api/v1/applications/status-missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));
    }

    @Test
    void registrationValidatesPathBodyAndBaseUrl() throws Exception {
        mockMvc.perform(put("/api/v1/applications/bad!code/instances/auth-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(put("/api/v1/applications/auth-api-validation/instances/auth-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(put("/api/v1/applications/auth-api-validation/instances/auth-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY.replace("http://auth-1:8080", "ftp://auth-1:21")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void actuatorHealthIsAvailable() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    private void register(String path) throws Exception {
        register(path, REGISTER_BODY);
    }

    private void register(String path, String body) throws Exception {
        mockMvc.perform(put(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }
}
