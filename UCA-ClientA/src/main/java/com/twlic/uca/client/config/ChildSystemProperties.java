package com.twlic.uca.client.config;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component("childSystemProperties")
@ConfigurationProperties(prefix = "uca.client")
public class ChildSystemProperties {

    private String applicationCode;
    private String applicationName;
    private String instanceId;
    private String version;
    private URI advertisedBaseUrl;
    private URI baseRegistryUrl;
    private Duration heartbeatInterval = Duration.ofSeconds(10);
    private String apiPrefix;
    private String callPeerPath;
    private String targetApplicationCode;
    private String targetPingPath;

    public String getApplicationCode() {
        return applicationCode;
    }

    public void setApplicationCode(String applicationCode) {
        this.applicationCode = applicationCode;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public URI getAdvertisedBaseUrl() {
        return advertisedBaseUrl;
    }

    public void setAdvertisedBaseUrl(URI advertisedBaseUrl) {
        this.advertisedBaseUrl = advertisedBaseUrl;
    }

    public URI getBaseRegistryUrl() {
        return baseRegistryUrl;
    }

    public void setBaseRegistryUrl(URI baseRegistryUrl) {
        this.baseRegistryUrl = baseRegistryUrl;
    }

    public Duration getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public void setHeartbeatInterval(Duration heartbeatInterval) {
        if (heartbeatInterval == null || heartbeatInterval.isZero() || heartbeatInterval.isNegative()) {
            throw new IllegalArgumentException("heartbeatInterval must be positive");
        }
        this.heartbeatInterval = heartbeatInterval;
    }

    public String getApiPrefix() {
        return apiPrefix;
    }

    public void setApiPrefix(String apiPrefix) {
        this.apiPrefix = apiPrefix;
    }

    public String getCallPeerPath() {
        return callPeerPath;
    }

    public void setCallPeerPath(String callPeerPath) {
        this.callPeerPath = callPeerPath;
    }

    public String getTargetApplicationCode() {
        return targetApplicationCode;
    }

    public void setTargetApplicationCode(String targetApplicationCode) {
        this.targetApplicationCode = targetApplicationCode;
    }

    public String getTargetPingPath() {
        return targetPingPath;
    }

    public void setTargetPingPath(String targetPingPath) {
        this.targetPingPath = targetPingPath;
    }
}
