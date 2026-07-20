package com.twlic.uca.client.integration;

import java.util.Map;

import com.twlic.uca.client.config.ChildSystemProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class UcaBaseClient {

    private final ChildSystemProperties properties;
    private final RestClient restClient;

    public UcaBaseClient(ChildSystemProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder
                .baseUrl(stripTrailingSlash(properties.getBaseRegistryUrl().toString()))
                .build();
    }

    public void register() {
        restClient.put()
                .uri("/api/v1/applications/{applicationCode}/instances/{instanceId}",
                        properties.getApplicationCode(), properties.getInstanceId())
                .body(new BaseRegistrationRequest(
                        properties.getApplicationName(),
                        properties.getAdvertisedBaseUrl(),
                        properties.getVersion(),
                        Map.of("type", "business-system")))
                .retrieve()
                .toBodilessEntity();
    }

    public boolean heartbeat() {
        try {
            restClient.put()
                    .uri("/api/v1/applications/{applicationCode}/instances/{instanceId}/heartbeat",
                            properties.getApplicationCode(), properties.getInstanceId())
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return false;
            }
            throw exception;
        }
    }

    public void deregister() {
        restClient.delete()
                .uri("/api/v1/applications/{applicationCode}/instances/{instanceId}",
                        properties.getApplicationCode(), properties.getInstanceId())
                .retrieve()
                .toBodilessEntity();
    }

    public BaseInstanceResponse discover(String applicationCode) {
        return restClient.get()
                .uri("/api/v1/applications/{applicationCode}/instances/next", applicationCode)
                .retrieve()
                .body(BaseInstanceResponse.class);
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
