package com.twlic.uca.client.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.twlic.uca.client.core.support.TestHttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class UcaClientTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-21T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void registersConfiguredMetadata() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.stub("POST", "/api/v1/applications/store/instances", 201,
                    "{\"instanceId\":\"base-issued-1\"}");

            UcaClient client = client(server.baseUrl());
            client.register();

            assertThat(server.requests().getFirst().body())
                    .contains("\"applicationName\":\"存储服务\"")
                    .contains("\"baseUrl\":\"http://127.0.0.1:20101\"")
                    .contains("\"uca.api-prefix\":\"/api/v1/store\"")
                    .contains("\"zone\":\"cn-east-1\"")
                    .contains("\"type\":\"business-system\"");
            assertThat(client.instanceId()).isEqualTo("base-issued-1");
        }
    }

    @Test
    void refreshesTheInMemorySecretOnRegistrationAndHeartbeat() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.stub("POST", "/api/v1/applications/store/instances", 201,
                    "{\"instanceId\":\"base-issued-1\"}",
                    Map.of(UcaClient.REGISTRATION_SECRET_HEADER, "base-secret-1"));
            server.stub("PUT", "/api/v1/applications/store/instances/base-issued-1/heartbeat", 200,
                    "{}",
                    Map.of(UcaClient.REGISTRATION_SECRET_HEADER, "base-secret-2"));
            UcaClientProperties properties = properties(server.baseUrl());
            UcaClient client = new UcaClient(server.baseUrl(), properties, RestClient.builder(), FIXED_CLOCK);

            client.register();
            assertThat(properties.getInternalSecret()).isEqualTo("base-secret-1");
            assertThat(properties.getPreviousInternalSecret()).isEqualTo("test-secret");

            assertThat(client.heartbeat()).isTrue();
            assertThat(properties.getInternalSecret()).isEqualTo("base-secret-2");
            assertThat(properties.getPreviousInternalSecret()).isEqualTo("base-secret-1");
        }
    }

    @Test
    void reportsMissingHeartbeatAndSupportsDeregistration() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.stub("POST", "/api/v1/applications/store/instances", 201,
                    "{\"instanceId\":\"store-1\"}");
            server.stub("PUT", "/api/v1/applications/store/instances/store-1/heartbeat", 404, "{}");
            server.stub("DELETE", "/api/v1/applications/store/instances/store-1", 204, "");
            UcaClient client = client(server.baseUrl());

            client.register();
            assertThat(client.heartbeat()).isFalse();
            client.register();
            client.deregister();

            assertThat(server.requests())
                    .extracting(TestHttpServer.RecordedRequest::method)
                    .containsExactly("POST", "PUT", "POST", "DELETE");
        }
    }

    @Test
    void refreshesTheLocalDirectoryAndCallsTheSelectedInstance() throws Exception {
        try (TestHttpServer base = new TestHttpServer();
             TestHttpServer target = new TestHttpServer()) {
            base.stub("GET", "/api/v1/applications", 200, applicationJson(target.baseUrl(), "ONLINE"));
            target.stub("GET", "/api/v1/audit/ping", 200, """
                    {
                      "code":0,
                      "error":null,
                      "message":"SUCCESS",
                      "data":{"message":"pong"},
                      "requestId":"request-1"
                    }
                    """);
            UcaClient client = client(base.baseUrl());

            client.refreshServices();
            PingResponse response = client.get("audit", "/ping")
                    .header(UcaServiceSignature.REQUEST_ID, "request-1")
                    .retrieve(PingResponse.class);

            assertThat(client.availableServiceNames()).containsExactly("audit");
            assertThat(response.message()).isEqualTo("pong");
            assertThat(base.requests()).extracting(TestHttpServer.RecordedRequest::path)
                    .containsExactly("/api/v1/applications");
            assertThat(target.requests().getFirst().header(UcaServiceSignature.CALLER_APPLICATION))
                    .isEqualTo("store");
            assertThat(target.requests().getFirst().header(UcaServiceSignature.SIGNATURE)).isNotBlank();
        }
    }

    @Test
    void supportsMissingPrefixesAndDoesNotDuplicateCompletePaths() throws Exception {
        try (TestHttpServer base = new TestHttpServer();
             TestHttpServer target = new TestHttpServer()) {
            base.stub("GET", "/api/v1/applications", 200,
                    applicationJsonWithoutApiPrefix(target.baseUrl()));
            target.stub("GET", "/ping", 200, successfulPingResponse());
            target.stub("GET", "/api/v1/audit/ping", 200, successfulPingResponse());
            UcaClient client = client(base.baseUrl());

            client.refreshServices();
            client.get("audit", "/ping").retrieve(PingResponse.class);

            base.stub("GET", "/api/v1/applications", 200,
                    applicationJson(target.baseUrl(), "ONLINE"));
            client.refreshServices();
            client.get("audit", "/api/v1/audit/ping").retrieve(PingResponse.class);

            assertThat(target.requests())
                    .extracting(TestHttpServer.RecordedRequest::path)
                    .containsExactly("/ping", "/api/v1/audit/ping");
        }
    }

    @Test
    void distinguishesMissingAndOfflineServices() throws Exception {
        try (TestHttpServer base = new TestHttpServer()) {
            base.stub("GET", "/api/v1/applications", 200,
                    applicationJson(URI.create("http://127.0.0.1:29999"), "OFFLINE"));
            UcaClient client = client(base.baseUrl());
            client.refreshServices();

            assertThatThrownBy(() -> client.discover("missing"))
                    .isInstanceOfSatisfying(UcaException.class,
                            exception -> assertThat(exception.code()).isEqualTo(10004));
            assertThatThrownBy(() -> client.discover("audit"))
                    .isInstanceOfSatisfying(UcaException.class,
                            exception -> assertThat(exception.code()).isEqualTo(10005));
        }
    }

    @Test
    void getSwitchesInstanceOnceButPostDoesNotRetry() throws Exception {
        try (TestHttpServer base = new TestHttpServer();
             TestHttpServer target = new TestHttpServer()) {
            base.stub("GET", "/api/v1/applications", 200, twoInstanceJson(target.baseUrl()));
            target.stub("GET", "/api/v1/audit/ping", 200, """
                    {"code":0,"error":null,"message":"SUCCESS","data":{"message":"pong"},"requestId":"r1"}
                    """);
            target.stub("POST", "/api/v1/audit/ping", 200, """
                    {"code":0,"error":null,"message":"SUCCESS","data":{"message":"pong"},"requestId":"r2"}
                    """);

            UcaClient getClient = client(base.baseUrl());
            getClient.refreshServices();
            assertThat(getClient.get("audit", "/ping").retrieve(PingResponse.class).message())
                    .isEqualTo("pong");

            UcaClient postClient = client(base.baseUrl());
            postClient.refreshServices();
            assertThatThrownBy(() -> postClient.post("audit", "/ping")
                    .retrieve(PingResponse.class))
                    .isInstanceOfSatisfying(UcaException.class,
                            exception -> assertThat(exception.code()).isEqualTo(10006));
            assertThat(target.requests()).extracting(TestHttpServer.RecordedRequest::method)
                    .containsExactly("GET");
        }
    }

    @Test
    void samplesJvmSelfLoad() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            UcaSelfLoad load = client(server.baseUrl()).selfLoad();

            assertThat(load.sampledAt()).isEqualTo(FIXED_CLOCK.instant());
            assertThat(load.processCpuLoad()).isBetween(-1.0, 1.0);
            assertThat(load.heapUsedBytes()).isNotNegative();
            assertThat(load.liveThreadCount()).isPositive();
            assertThat(load.availableProcessors()).isPositive();
        }
    }

    @Test
    void lifecycleRegistersRefreshesAndDeregistersAutomatically() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.stub("POST", "/api/v1/applications/store/instances", 201,
                    "{\"instanceId\":\"store-1\"}");
            server.stub("GET", "/api/v1/applications", 200, "[]");
            server.stub("DELETE", "/api/v1/applications/store/instances/store-1", 204, "");
            UcaClientProperties properties = properties(server.baseUrl());
            RegistrationLifecycle lifecycle =
                    new RegistrationLifecycle(
                            new UcaClient(server.baseUrl(), properties, RestClient.builder(), FIXED_CLOCK),
                            properties);

            lifecycle.registerOnStartup();
            assertThat(lifecycle.isRegistered()).isTrue();

            lifecycle.deregisterOnShutdown();
            assertThat(lifecycle.isRegistered()).isFalse();
        }
    }

    @Test
    void startupAndMaintenanceCannotRegisterTheSameJvmTwice() throws Exception {
        UcaClientProperties properties = properties(URI.create("http://127.0.0.1:9"));
        BlockingRegistrationClient client = new BlockingRegistrationClient(properties);
        RegistrationLifecycle lifecycle = new RegistrationLifecycle(client, properties);
        Thread startup = Thread.ofPlatform().start(lifecycle::registerOnStartup);
        assertThat(client.registrationStarted.await(1, TimeUnit.SECONDS)).isTrue();
        Thread maintenance = Thread.ofPlatform().start(lifecycle::maintainRegistration);

        try {
            waitUntilBlocked(maintenance);
        } finally {
            client.finishRegistration.countDown();
        }
        startup.join(1000);
        maintenance.join(1000);

        assertThat(client.registrations).hasValue(1);
    }

    private UcaClient client(URI baseRegistryUrl) {
        UcaClientProperties properties = properties(baseRegistryUrl);
        properties.assignInstanceId("store-test-1");
        return new UcaClient(baseRegistryUrl, properties, RestClient.builder(), FIXED_CLOCK);
    }

    private UcaClientProperties properties(URI baseRegistryUrl) {
        UcaClientProperties properties = new UcaClientProperties();
        properties.setCode("store");
        properties.setName("存储服务");
        properties.setHost("127.0.0.1:20101");
        properties.setVersion("1.0.0");
        properties.setPrefix("api/v1/store");
        properties.setInternalSecret("test-secret");
        properties.setMetadata(Map.of("type", "business-system", "zone", "cn-east-1"));
        return properties;
    }

    private static void waitUntilBlocked(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (thread.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertThat(thread.getState()).isEqualTo(Thread.State.BLOCKED);
    }

    private String applicationJson(URI targetBaseUrl, String status) {
        return """
                [{
                  "applicationCode":"audit",
                  "applicationName":"审计服务",
                  "instances":[{
                    "applicationCode":"audit",
                    "applicationName":"审计服务",
                    "instanceId":"audit-1",
                    "baseUrl":"%s",
                    "version":"1.0.0",
                    "metadata":{"uca.api-prefix":"/api/v1/audit"},
                    "status":"%s",
                    "registeredAt":null,
                    "lastHeartbeatAt":null,
                    "statusChangedAt":null
                  }]
                }]
                """.formatted(targetBaseUrl, status);
    }

    private String twoInstanceJson(URI targetBaseUrl) {
        return """
                [{
                  "applicationCode":"audit",
                  "applicationName":"审计服务",
                  "instances":[
                    {
                      "applicationCode":"audit",
                      "applicationName":"审计服务",
                      "instanceId":"audit-1",
                      "baseUrl":"http://127.0.0.1:1",
                      "version":"1.0.0",
                      "metadata":{"uca.api-prefix":"/api/v1/audit"},
                      "status":"ONLINE",
                      "registeredAt":null,
                      "lastHeartbeatAt":null,
                      "statusChangedAt":null
                    },
                    {
                      "applicationCode":"audit",
                      "applicationName":"审计服务",
                      "instanceId":"audit-2",
                      "baseUrl":"%s",
                      "version":"1.0.0",
                      "metadata":{"uca.api-prefix":"/api/v1/audit"},
                      "status":"ONLINE",
                      "registeredAt":null,
                      "lastHeartbeatAt":null,
                      "statusChangedAt":null
                    }
                  ]
                }]
                """.formatted(targetBaseUrl);
    }

    private String applicationJsonWithoutApiPrefix(URI targetBaseUrl) {
        return """
                [{
                  "applicationCode":"audit",
                  "applicationName":"审计服务",
                  "instances":[{
                    "applicationCode":"audit",
                    "applicationName":"审计服务",
                    "instanceId":"audit-1",
                    "baseUrl":"%s",
                    "version":"1.0.0",
                    "metadata":{},
                    "status":"ONLINE",
                    "registeredAt":null,
                    "lastHeartbeatAt":null,
                    "statusChangedAt":null
                  }]
                }]
                """.formatted(targetBaseUrl);
    }

    private String successfulPingResponse() {
        return """
                {"code":0,"error":null,"message":"SUCCESS","data":{"message":"pong"},"requestId":"r1"}
                """;
    }

    private record PingResponse(String message) {
    }

    private static final class BlockingRegistrationClient extends UcaClient {

        private final CountDownLatch registrationStarted = new CountDownLatch(1);
        private final CountDownLatch finishRegistration = new CountDownLatch(1);
        private final AtomicInteger registrations = new AtomicInteger();

        private BlockingRegistrationClient(UcaClientProperties properties) {
            super(URI.create("http://127.0.0.1:9"), properties, RestClient.builder(), FIXED_CLOCK);
        }

        @Override
        public void register() {
            registrations.incrementAndGet();
            registrationStarted.countDown();
            try {
                finishRegistration.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public boolean heartbeat() {
            return true;
        }

        @Override
        public void refreshServices() {
        }
    }
}
