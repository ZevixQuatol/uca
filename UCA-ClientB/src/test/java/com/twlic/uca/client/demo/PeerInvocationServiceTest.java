package com.twlic.uca.client.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.twlic.uca.client.config.ChildSystemProperties;
import com.twlic.uca.client.integration.UcaBaseClient;
import com.twlic.uca.client.support.TestHttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class PeerInvocationServiceTest {

    @Test
    void discoversAndCallsThePeerDirectly() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.stub("GET", "/api/v1/applications/client-a/instances/next", 200,
                    discoveryJson(server.baseUrl()));
            server.stub("GET", "/api/v1/client-a/demo/ping", 200, """
                    {
                      "applicationCode":"client-a",
                      "instanceId":"client-a-1",
                      "message":"pong from client-a",
                      "timestamp":"2026-07-16T00:00:00Z"
                    }
                    """);
            ChildSystemProperties properties = properties(server.baseUrl());
            UcaBaseClient baseClient = new UcaBaseClient(properties, RestClient.builder());
            PeerInvocationService service = new PeerInvocationService(
                    baseClient,
                    properties,
                    RestClient.builder(),
                    Clock.fixed(Instant.parse("2026-07-16T00:00:00Z"), ZoneOffset.UTC));

            PeerCallResponse response = service.callPeer();

            assertThat(response.callerApplicationCode()).isEqualTo("client-b");
            assertThat(response.targetApplicationCode()).isEqualTo("client-a");
            assertThat(response.targetInstanceId()).isEqualTo("client-a-1");
            assertThat(response.targetResponse().message()).isEqualTo("pong from client-a");
            assertThat(server.requests())
                    .extracting(TestHttpServer.RecordedRequest::path)
                    .containsExactly(
                            "/api/v1/applications/client-a/instances/next",
                            "/api/v1/client-a/demo/ping");
        }
    }

    @Test
    void reportsTargetUnavailableWhenBaseHasNoOnlinePeer() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.stub("GET", "/api/v1/applications/client-a/instances/next", 503, "{}");
            ChildSystemProperties properties = properties(server.baseUrl());
            PeerInvocationService service = new PeerInvocationService(
                    new UcaBaseClient(properties, RestClient.builder()),
                    properties,
                    RestClient.builder(),
                    Clock.systemUTC());

            assertThatThrownBy(service::callPeer)
                    .isInstanceOfSatisfying(DemoCallException.class, exception -> {
                        assertThat(exception.code()).isEqualTo("TARGET_UNAVAILABLE");
                        assertThat(exception.status().value()).isEqualTo(503);
                    });
        }
    }

    @Test
    void reportsBadGatewayWhenTheSelectedPeerCallFails() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.stub("GET", "/api/v1/applications/client-a/instances/next", 200,
                    discoveryJson(server.baseUrl()));
            server.stub("GET", "/api/v1/client-a/demo/ping", 500, "{}");
            ChildSystemProperties properties = properties(server.baseUrl());
            PeerInvocationService service = new PeerInvocationService(
                    new UcaBaseClient(properties, RestClient.builder()),
                    properties,
                    RestClient.builder(),
                    Clock.systemUTC());

            assertThatThrownBy(service::callPeer)
                    .isInstanceOfSatisfying(DemoCallException.class, exception -> {
                        assertThat(exception.code()).isEqualTo("TARGET_CALL_FAILED");
                        assertThat(exception.status().value()).isEqualTo(502);
                    });
        }
    }

    private ChildSystemProperties properties(URI serverUrl) {
        ChildSystemProperties properties = new ChildSystemProperties();
        properties.setApplicationCode("client-b");
        properties.setApplicationName("子业务系统 B");
        properties.setInstanceId("client-b-1");
        properties.setVersion("0.0.1");
        properties.setAdvertisedBaseUrl(URI.create("http://127.0.0.1:48102"));
        properties.setBaseRegistryUrl(serverUrl);
        properties.setTargetApplicationCode("client-a");
        properties.setTargetPingPath("/api/v1/client-a/demo/ping");
        return properties;
    }

    private String discoveryJson(URI serverUrl) {
        return """
                {
                  "applicationCode":"client-a",
                  "applicationName":"子业务系统 A",
                  "instanceId":"client-a-1",
                  "baseUrl":"%s",
                  "version":"0.0.1",
                  "metadata":{},
                  "status":"ONLINE",
                  "registeredAt":"2026-07-16T00:00:00Z",
                  "lastHeartbeatAt":"2026-07-16T00:00:00Z",
                  "statusChangedAt":"2026-07-16T00:00:00Z"
                }
                """.formatted(serverUrl);
    }
}
