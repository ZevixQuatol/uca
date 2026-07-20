package com.twlic.uca.client.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import com.twlic.uca.client.config.ChildSystemProperties;
import com.twlic.uca.client.support.TestHttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class UcaBaseClientTest {

    @Test
    void registersUsingTheUcaBaseContract() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.stub("PUT", "/api/v1/applications/client-b/instances/client-b-1", 201, "");
            UcaBaseClient client = client(server.baseUrl());

            client.register();

            TestHttpServer.RecordedRequest request = server.requests().getFirst();
            assertThat(request.method()).isEqualTo("PUT");
            assertThat(request.path()).isEqualTo("/api/v1/applications/client-b/instances/client-b-1");
            assertThat(request.body())
                    .contains("\"applicationName\":\"子业务系统 B\"")
                    .contains("\"baseUrl\":\"http://127.0.0.1:48102\"")
                    .contains("\"version\":\"0.0.1\"");
        }
    }

    @Test
    void heartbeatSignalsWhenBaseForgotTheInstance() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.stub("PUT", "/api/v1/applications/client-b/instances/client-b-1/heartbeat", 404, "{}");

            assertThat(client(server.baseUrl()).heartbeat()).isFalse();
        }
    }

    @Test
    void heartbeatSucceedsForAKnownInstance() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.stub("PUT", "/api/v1/applications/client-b/instances/client-b-1/heartbeat", 200, "{}");

            assertThat(client(server.baseUrl()).heartbeat()).isTrue();
        }
    }

    @Test
    void discoversOneSelectedInstanceAndDeregisters() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.stub("GET", "/api/v1/applications/client-a/instances/next", 200, """
                    {
                      "applicationCode":"client-a",
                      "applicationName":"子业务系统 A",
                      "instanceId":"client-a-1",
                      "baseUrl":"http://127.0.0.1:48101",
                      "version":"0.0.1",
                      "metadata":{},
                      "status":"ONLINE",
                      "registeredAt":"2026-07-16T00:00:00Z",
                      "lastHeartbeatAt":"2026-07-16T00:00:00Z",
                      "statusChangedAt":"2026-07-16T00:00:00Z"
                    }
                    """);
            server.stub("DELETE", "/api/v1/applications/client-b/instances/client-b-1", 204, "");
            UcaBaseClient client = client(server.baseUrl());

            BaseInstanceResponse instance = client.discover("client-a");
            client.deregister();

            assertThat(instance.instanceId()).isEqualTo("client-a-1");
            assertThat(instance.baseUrl()).isEqualTo(URI.create("http://127.0.0.1:48101"));
            assertThat(server.requests())
                    .extracting(TestHttpServer.RecordedRequest::method)
                    .containsExactly("GET", "DELETE");
        }
    }

    private UcaBaseClient client(URI baseRegistryUrl) {
        return new UcaBaseClient(properties(baseRegistryUrl), RestClient.builder());
    }

    private ChildSystemProperties properties(URI baseRegistryUrl) {
        ChildSystemProperties properties = new ChildSystemProperties();
        properties.setApplicationCode("client-b");
        properties.setApplicationName("子业务系统 B");
        properties.setInstanceId("client-b-1");
        properties.setVersion("0.0.1");
        properties.setAdvertisedBaseUrl(URI.create("http://127.0.0.1:48102"));
        properties.setBaseRegistryUrl(baseRegistryUrl);
        properties.setTargetApplicationCode("client-a");
        properties.setTargetPingPath("/api/v1/client-a/demo/ping");
        return properties;
    }
}
