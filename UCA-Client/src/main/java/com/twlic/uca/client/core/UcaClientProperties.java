package com.twlic.uca.client.core;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "uca.client")
public class UcaClientProperties {

    private static final String IDENTIFIER_PATTERN = "[A-Za-z0-9._-]+";

    @NotBlank
    @Size(max = 255)
    private String host;

    @NotBlank
    @Pattern(regexp = IDENTIFIER_PATTERN)
    private String code;

    @NotBlank
    @Size(max = 100)
    private String name;

    @Size(max = 50)
    private String version;

    private String prefix = "";

    @NotNull
    private Duration interval = Duration.ofSeconds(5);

    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(2);

    @NotNull
    private Duration readTimeout = Duration.ofSeconds(5);

    @NotNull
    private Duration signatureValidity = Duration.ofSeconds(30);

    private volatile String internalSecret = "";

    private volatile String previousInternalSecret = "";

    private int maxBodyBytes = 1024 * 1024;

    private Map<String, String> metadata = Map.of();

    private volatile String instanceId;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public URI getAdvertisedBaseUrl() {
        String value = host == null ? "" : host.trim();
        URI uri;
        try {
            uri = URI.create(value.contains("://") ? value : "http://" + value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("host must be a valid HTTP host and port", exception);
        }
        String scheme = uri.getScheme();
        if (uri.getHost() == null
                || uri.getPort() < 0
                || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || (uri.getPath() != null && !uri.getPath().isBlank() && !"/".equals(uri.getPath()))
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException("host must be a valid HTTP host and port");
        }
        return URI.create(uri.getScheme().toLowerCase(Locale.ROOT) + "://" + uri.getAuthority());
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = normalizePrefix(prefix);
    }

    public Duration getInterval() {
        return interval;
    }

    public void setInterval(Duration interval) {
        this.interval = requirePositive(interval, "interval");
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = requirePositive(connectTimeout, "connectTimeout");
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = requirePositive(readTimeout, "readTimeout");
    }

    public Duration getSignatureValidity() {
        return signatureValidity;
    }

    public void setSignatureValidity(Duration signatureValidity) {
        this.signatureValidity = requirePositive(signatureValidity, "signatureValidity");
    }

    public String getInternalSecret() {
        return internalSecret;
    }

    public void setInternalSecret(String internalSecret) {
        updateInternalSecret(internalSecret);
    }

    public String getPreviousInternalSecret() {
        return previousInternalSecret;
    }

    void updateInternalSecret(String internalSecret) {
        String next = internalSecret == null ? "" : internalSecret.trim();
        if (next.isBlank()) {
            return;
        }
        synchronized (this) {
            if (!next.equals(this.internalSecret)) {
                previousInternalSecret = this.internalSecret;
                this.internalSecret = next;
            }
        }
    }

    public int getMaxBodyBytes() {
        return maxBodyBytes;
    }

    public void setMaxBodyBytes(int maxBodyBytes) {
        if (maxBodyBytes <= 0) {
            throw new IllegalArgumentException("maxBodyBytes must be positive");
        }
        this.maxBodyBytes = maxBodyBytes;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    void assignInstanceId(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            throw new UcaException(UcaResponseCode.UCA_RESPONSE_INVALID, "UCA-Base did not issue an instance ID");
        }
        this.instanceId = instanceId;
    }

    void clearInstanceId() {
        instanceId = null;
    }

    String requireInstanceId() {
        if (instanceId == null || instanceId.isBlank()) {
            throw new UcaException(UcaResponseCode.UCA_CALLER_NOT_ONLINE);
        }
        return instanceId;
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static String normalizePrefix(String value) {
        if (value == null || value.isBlank() || "/".equals(value.trim())) {
            return "";
        }
        String normalized = value.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.contains("?")
                || normalized.contains("#")
                || normalized.contains("//")
                || normalized.contains("..")
                || normalized.toLowerCase(Locale.ROOT).contains("%2e")) {
            throw new IllegalArgumentException("prefix must be a safe relative URL path");
        }
        return normalized;
    }
}
