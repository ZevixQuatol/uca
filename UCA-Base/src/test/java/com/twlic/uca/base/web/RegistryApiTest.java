package com.twlic.uca.base.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

import com.twlic.uca.base.UcaBaseApplication;
import com.twlic.uca.base.registry.InstanceRegistry;
import com.twlic.uca.base.config.RegistryProperties;
import com.twlic.uca.base.security.UcaSecretManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes = UcaBaseApplication.StandaloneConfiguration.class, properties = {
        "uca.base.heartbeat-timeout=1h",
        "uca.base.offline-retention=1h"
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
    private ObjectMapper objectMapper;

    @Autowired
    private InstanceRegistry registry;

    @Autowired
    private RegistryProperties properties;

    @Autowired
    private UcaSecretManager secretManager;

    @Test
    void registrationReturnsBaseIssuedInstanceIds() throws Exception {
        String first = register("auth-api-registration");
        String second = register("auth-api-registration");

        assertThat(first).isNotBlank().isNotEqualTo(second);
    }

    @Test
    void rotatesTheInMemorySecretAndReturnsItOnlyOnTheControlPlane() throws Exception {
        var registration = mockMvc.perform(post("/api/v1/applications/secret-api/instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse();
        String firstSecret = registration.getHeader(UcaSecretManager.SECRET_HEADER);
        String instanceId = objectMapper.readTree(registration.getContentAsString(StandardCharsets.UTF_8))
                .get("instanceId")
                .asText();

        assertThat(properties.getSecretInterval()).isEqualTo(Duration.ofMinutes(10));
        assertThat(firstSecret).isNotBlank();
        assertThat(registration.getContentAsString(StandardCharsets.UTF_8)).doesNotContain(firstSecret);

        secretManager.rotate();
        var heartbeat = mockMvc.perform(put(instancePath("secret-api", instanceId) + "/heartbeat"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
        String secondSecret = heartbeat.getHeader(UcaSecretManager.SECRET_HEADER);
        assertThat(secondSecret).isNotBlank().isNotEqualTo(firstSecret);

        String directory = mockMvc.perform(get("/api/v1/applications"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(directory).doesNotContain(firstSecret, secondSecret);
    }

    @Test
    void heartbeatRenewsAKnownInstance() throws Exception {
        String instanceId = register("auth-api-heartbeat");

        mockMvc.perform(put(instancePath("auth-api-heartbeat", instanceId) + "/heartbeat"))
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
        String instanceId = register("auth-api-delete");
        String path = instancePath("auth-api-delete", instanceId);

        mockMvc.perform(delete(path)).andExpect(status().isNoContent());
        mockMvc.perform(delete(path)).andExpect(status().isNoContent());
    }

    @Test
    void discoveryReturnsRoundRobinInstances() throws Exception {
        String firstRegistered = register("auth-api-discovery");
        String secondRegistered = register(
                "auth-api-discovery",
                REGISTER_BODY.replace("auth-1:8080", "auth-2:8080"));

        String firstDiscovered = discover("auth-api-discovery");
        String secondDiscovered = discover("auth-api-discovery");
        String thirdDiscovered = discover("auth-api-discovery");

        assertThat(Set.of(firstDiscovered, secondDiscovered))
                .containsExactlyInAnyOrder(firstRegistered, secondRegistered);
        assertThat(thirdDiscovered).isEqualTo(firstDiscovered);
    }

    @Test
    void discoveryDistinguishesUnknownApplicationFromNoOnlineInstance() throws Exception {
        mockMvc.perform(get("/api/v1/applications/no-such-application/instances/next"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));

        register("auth-api-offline");
        registry.updateLifecycle(Duration.ZERO, Duration.ofHours(1));

        mockMvc.perform(get("/api/v1/applications/auth-api-offline/instances/next"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("NO_ONLINE_INSTANCE"));
    }

    @Test
    void statusEndpointsReturnApplicationSnapshots() throws Exception {
        String instanceId = register("auth-api-status");

        mockMvc.perform(get("/api/v1/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].applicationCode", hasItem("auth-api-status")));
        mockMvc.perform(get("/api/v1/applications/auth-api-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationCode").value("auth-api-status"))
                .andExpect(jsonPath("$.instances[0].instanceId").value(instanceId))
                .andExpect(jsonPath("$.instances[0].metadata.zone").value("default"));
    }

    @Test
    void statusReturnsNotFoundForUnknownApplication() throws Exception {
        mockMvc.perform(get("/api/v1/applications/status-missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));
    }

    @Test
    void registrationValidatesPathBodyAndBaseUrl() throws Exception {
        mockMvc.perform(post("/api/v1/applications/bad!code/instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/applications/auth-api-validation/instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/applications/auth-api-validation/instances")
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

    private String register(String applicationCode) throws Exception {
        return register(applicationCode, REGISTER_BODY);
    }

    private String register(String applicationCode, String body) throws Exception {
        String response = mockMvc.perform(post("/api/v1/applications/{applicationCode}/instances", applicationCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.applicationCode").value(applicationCode))
                .andExpect(jsonPath("$.instanceId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("ONLINE"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).get("instanceId").asText();
    }

    private String discover(String applicationCode) throws Exception {
        String response = mockMvc.perform(get(
                        "/api/v1/applications/{applicationCode}/instances/next", applicationCode))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).get("instanceId").asText();
    }

    private String instancePath(String applicationCode, String instanceId) {
        return "/api/v1/applications/%s/instances/%s".formatted(applicationCode, instanceId);
    }
}
