package com.twlic.uca.client.demo;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;

import com.twlic.uca.client.config.ChildSystemProperties;
import com.twlic.uca.client.integration.BaseInstanceResponse;
import com.twlic.uca.client.integration.UcaBaseClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class PeerInvocationService {

    private final UcaBaseClient baseClient;
    private final ChildSystemProperties properties;
    private final RestClient restClient;
    private final Clock clock;

    public PeerInvocationService(
            UcaBaseClient baseClient,
            ChildSystemProperties properties,
            RestClient.Builder builder,
            Clock clock) {
        this.baseClient = baseClient;
        this.properties = properties;
        this.restClient = builder.build();
        this.clock = clock;
    }

    public PingResponse ping() {
        return new PingResponse(
                properties.getApplicationCode(),
                properties.getInstanceId(),
                "pong from " + properties.getApplicationCode(),
                Instant.now(clock));
    }

    public PeerCallResponse callPeer() {
        BaseInstanceResponse target = discoverTarget();
        URI targetUri = appendPath(target.baseUrl(), properties.getTargetPingPath());
        try {
            PingResponse response = restClient.get()
                    .uri(targetUri)
                    .retrieve()
                    .body(PingResponse.class);
            return new PeerCallResponse(
                    properties.getApplicationCode(),
                    properties.getTargetApplicationCode(),
                    target.instanceId(),
                    target.baseUrl(),
                    response);
        } catch (RestClientException exception) {
            throw new DemoCallException(
                    "TARGET_CALL_FAILED",
                    HttpStatus.BAD_GATEWAY,
                    "Failed to call target application '%s' at %s"
                            .formatted(properties.getTargetApplicationCode(), targetUri),
                    exception);
        }
    }

    private BaseInstanceResponse discoverTarget() {
        try {
            return baseClient.discover(properties.getTargetApplicationCode());
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND
                    || exception.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
                throw new DemoCallException(
                        "TARGET_UNAVAILABLE",
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Target application '%s' has no discoverable instance"
                                .formatted(properties.getTargetApplicationCode()),
                        exception);
            }
            throw new DemoCallException(
                    "UCA_BASE_CALL_FAILED",
                    HttpStatus.BAD_GATEWAY,
                    "UCA-Base rejected the discovery request",
                    exception);
        } catch (RestClientException exception) {
            throw new DemoCallException(
                    "UCA_BASE_UNAVAILABLE",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "UCA-Base is unavailable",
                    exception);
        }
    }

    private static URI appendPath(URI baseUrl, String path) {
        String base = baseUrl.toString();
        String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return URI.create(normalizedBase + normalizedPath);
    }
}
