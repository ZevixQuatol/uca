package com.twlic.uca.client.core;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public class UcaClient {

    public static final String API_PREFIX_METADATA = "uca.api-prefix";
    public static final String REGISTRATION_SECRET_HEADER = "X-UCA-Registration-Secret";

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z0-9._-]+");
    private static final Set<String> FORWARDED_HEADERS = Set.of(
            HttpHeaders.AUTHORIZATION.toLowerCase(Locale.ROOT),
            HttpHeaders.ACCEPT.toLowerCase(Locale.ROOT),
            HttpHeaders.ACCEPT_LANGUAGE.toLowerCase(Locale.ROOT),
            HttpHeaders.CONTENT_TYPE.toLowerCase(Locale.ROOT),
            HttpHeaders.IF_MATCH.toLowerCase(Locale.ROOT),
            HttpHeaders.IF_NONE_MATCH.toLowerCase(Locale.ROOT),
            "traceparent",
            "tracestate",
            UcaServiceSignature.REQUEST_ID.toLowerCase(Locale.ROOT));

    private final UcaClientProperties properties;
    private final RestClient baseClient;
    private final RestClient targetClient;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final UcaServiceSignature signature;
    private final AtomicReference<Map<String, UcaApplication>> directory =
            new AtomicReference<>(Map.of());
    private final ConcurrentMap<String, AtomicInteger> cursors = new ConcurrentHashMap<>();

    public UcaClient(
            URI baseRegistryUrl,
            UcaClientProperties properties,
            RestClient.Builder builder,
            Clock clock) {
        this(
                baseRegistryUrl,
                properties,
                builder,
                clock,
                new ObjectMapper(),
                new UcaServiceSignature(properties, clock));
    }

    public UcaClient(
            URI baseRegistryUrl,
            UcaClientProperties properties,
            RestClient.Builder builder,
            Clock clock,
            ObjectMapper objectMapper,
            UcaServiceSignature signature) {

        this.properties = Objects.requireNonNull(properties);
        this.clock = Objects.requireNonNull(clock);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.signature = Objects.requireNonNull(signature);

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        this.baseClient = builder.clone()
                .requestFactory(requestFactory)
                .baseUrl(stripTrailingSlash(Objects.requireNonNull(baseRegistryUrl).toString()))
                .build();
        this.targetClient = builder.clone()
                .requestFactory(requestFactory)
                .build();
    }

    public void register() {
        ResponseEntity<UcaServiceInstance> response = baseClient.post()
                .uri("/api/v1/applications/{applicationCode}/instances", properties.getCode())
                .body(new RegistrationRequest(
                        properties.getName(),
                        properties.getAdvertisedBaseUrl(),
                        properties.getVersion(),
                        registrationMetadata()))
                .retrieve()
                .toEntity(UcaServiceInstance.class);
        updateRegistrationSecret(response.getHeaders());
        UcaServiceInstance instance = response.getBody();
        properties.assignInstanceId(instance == null ? null : instance.instanceId());
    }

    public boolean heartbeat() {
        String instanceId = properties.getInstanceId();
        if (instanceId == null || instanceId.isBlank()) {
            return false;
        }
        try {
            ResponseEntity<String> response = baseClient.put()
                    .uri("/api/v1/applications/{applicationCode}/instances/{instanceId}/heartbeat",
                            properties.getCode(), instanceId)
                    .retrieve()
                    .toEntity(String.class);
            updateRegistrationSecret(response.getHeaders());
            return true;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                properties.clearInstanceId();
                return false;
            }
            throw exception;
        }
    }

    public void deregister() {
        String instanceId = properties.getInstanceId();
        if (instanceId == null || instanceId.isBlank()) {
            return;
        }
        baseClient.delete()
                .uri("/api/v1/applications/{applicationCode}/instances/{instanceId}",
                        properties.getCode(), instanceId)
                .retrieve()
                .toBodilessEntity();
        properties.clearInstanceId();
    }

    public String instanceId() {
        return properties.getInstanceId();
    }

    public void refreshServices() {
        UcaApplication[] applications = baseClient.get()
                .uri("/api/v1/applications")
                .retrieve()
                .body(UcaApplication[].class);
        Map<String, UcaApplication> refreshed = new LinkedHashMap<>();
        if (applications != null) {
            for (UcaApplication application : applications) {
                refreshed.put(application.applicationCode(), application);
            }
        }
        directory.set(Map.copyOf(refreshed));
    }

    public UcaServiceInstance discover(String applicationCode) {
        List<UcaServiceInstance> instances = onlineInstances(applicationCode);
        int index = Math.floorMod(
                cursors.computeIfAbsent(applicationCode, ignored -> new AtomicInteger()).getAndIncrement(),
                instances.size());
        return instances.get(index);
    }

    public List<UcaApplication> services() {
        return directory.get().values().stream()
                .sorted(Comparator.comparing(UcaApplication::applicationCode))
                .toList();
    }

    public UcaApplication service(String applicationCode) {
        return directory.get().get(applicationCode);
    }

    public List<UcaServiceInstance> instances(String applicationCode) {
        UcaApplication application = service(applicationCode);
        return application == null ? List.of() : application.instances();
    }

    public List<String> availableServiceNames() {
        return services().stream()
                .filter(application -> !application.applicationCode().equals(properties.getCode()))
                .filter(application -> application.instances().stream()
                        .anyMatch(instance -> "ONLINE".equals(instance.status())))
                .map(UcaApplication::applicationCode)
                .toList();
    }

    public RequestSpec get(String serviceName, String relativePath) {
        return request(HttpMethod.GET, serviceName, relativePath);
    }

    public RequestSpec post(String serviceName, String relativePath) {
        return request(HttpMethod.POST, serviceName, relativePath);
    }

    public RequestSpec put(String serviceName, String relativePath) {
        return request(HttpMethod.PUT, serviceName, relativePath);
    }

    public RequestSpec patch(String serviceName, String relativePath) {
        return request(HttpMethod.PATCH, serviceName, relativePath);
    }

    public RequestSpec delete(String serviceName, String relativePath) {
        return request(HttpMethod.DELETE, serviceName, relativePath);
    }

    public RequestSpec request(HttpMethod method, String serviceName, String relativePath) {
        return new RequestSpec(method, serviceName, relativePath);
    }

    public byte[] forward(
            HttpMethod method,
            String serviceName,
            String relativePath,
            String rawQuery,
            HttpHeaders incomingHeaders,
            byte[] body) {

        validateServiceName(serviceName);
        URI relativeUri = validateRelativePath(relativePath);
        String combinedQuery = combineQuery(relativeUri.getRawQuery(), rawQuery);
        byte[] requestBody = body == null ? new byte[0] : body;
        if (requestBody.length > properties.getMaxBodyBytes()) {
            throw new UcaException(UcaResponseCode.UCA_INVALID_REQUEST, "UCA request body is too large");
        }

        List<UcaServiceInstance> instances = onlineInstances(serviceName);
        int start = Math.floorMod(
                cursors.computeIfAbsent(serviceName, ignored -> new AtomicInteger()).getAndIncrement(),
                instances.size());
        int attempts = method == HttpMethod.GET || method == HttpMethod.HEAD
                ? Math.min(2, instances.size())
                : 1;
        RestClientException lastFailure = null;

        for (int attempt = 0; attempt < attempts; attempt++) {
            UcaServiceInstance instance = instances.get((start + attempt) % instances.size());
            URI targetUri = targetUri(instance, relativeUri.getRawPath(), combinedQuery);
            String requestId = requestId(incomingHeaders);
            HttpHeaders headers = forwardedHeaders(incomingHeaders);
            signature.sign(
                    headers,
                    method,
                    targetUri.getRawPath(),
                    targetUri.getRawQuery(),
                    requestBody,
                    requestId);
            try {
                TargetResponse response = exchange(method, targetUri, headers, requestBody);
                validateTargetResponse(response);
                return response.body();
            } catch (RestClientException exception) {
                lastFailure = exception;
            }
        }

        if (isTimeout(lastFailure)) {
            throw new UcaException(
                    UcaResponseCode.UCA_TARGET_TIMEOUT,
                    "Target service call timed out",
                    lastFailure);
        }
        throw new UcaException(
                UcaResponseCode.UCA_TARGET_CONNECTION_FAILED,
                "Unable to connect to target service",
                lastFailure);
    }

    public UcaSelfLoad selfLoad() {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        java.lang.management.OperatingSystemMXBean operatingSystem =
                ManagementFactory.getOperatingSystemMXBean();
        double processCpuLoad = operatingSystem instanceof com.sun.management.OperatingSystemMXBean extended
                ? normalizeCpuLoad(extended.getProcessCpuLoad())
                : -1.0;
        return new UcaSelfLoad(
                clock.instant(),
                processCpuLoad,
                heap.getUsed(),
                heap.getMax(),
                ManagementFactory.getThreadMXBean().getThreadCount(),
                operatingSystem.getAvailableProcessors());
    }

    private List<UcaServiceInstance> onlineInstances(String serviceName) {
        UcaApplication application = directory.get().get(serviceName);
        if (application == null) {
            throw new UcaException(
                    UcaResponseCode.UCA_SERVICE_NOT_FOUND,
                    "Service '%s' does not exist".formatted(serviceName));
        }
        List<UcaServiceInstance> online = application.instances().stream()
                .filter(instance -> "ONLINE".equals(instance.status()))
                .sorted(Comparator.comparing(UcaServiceInstance::instanceId))
                .toList();
        if (online.isEmpty()) {
            throw new UcaException(
                    UcaResponseCode.UCA_SERVICE_OFFLINE,
                    "Service '%s' has no online instance".formatted(serviceName));
        }
        return online;
    }

    private TargetResponse exchange(
            HttpMethod method,
            URI targetUri,
            HttpHeaders headers,
            byte[] body) {

        RestClient.RequestBodySpec request = targetClient.method(method)
                .uri(targetUri)
                .headers(targetHeaders -> targetHeaders.addAll(headers));
        if (body.length > 0) {
            request.body(body);
        }
        return request.exchange((clientRequest, clientResponse) -> new TargetResponse(
                clientResponse.getStatusCode().value(),
                clientResponse.getBody().readAllBytes()));
    }

    private void validateTargetResponse(TargetResponse response) {
        if (response.httpStatus() != HttpStatus.OK.value()) {
            throw new UcaException(
                    UcaResponseCode.UCA_RESPONSE_INVALID,
                    "Target service returned HTTP status " + response.httpStatus());
        }
        try {
            JsonNode node = objectMapper.readTree(response.body());
            if (node == null || !node.isObject() || !node.path("code").canConvertToInt()) {
                throw invalidResponse();
            }
        } catch (UcaException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new UcaException(
                    UcaResponseCode.UCA_RESPONSE_INVALID,
                    "Target response is not valid UCA JSON",
                    exception);
        }
    }

    private HttpHeaders forwardedHeaders(HttpHeaders incomingHeaders) {
        HttpHeaders result = new HttpHeaders();
        if (incomingHeaders == null) {
            return result;
        }
        incomingHeaders.forEach((name, values) -> {
            if (FORWARDED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                result.put(name, new ArrayList<>(values));
            }
        });
        return result;
    }

    private static String requestId(HttpHeaders headers) {
        String requestId = headers == null ? null : headers.getFirst(UcaServiceSignature.REQUEST_ID);
        return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
    }

    private static void validateServiceName(String serviceName) {
        if (serviceName == null || !IDENTIFIER_PATTERN.matcher(serviceName).matches()) {
            throw new UcaException(UcaResponseCode.UCA_INVALID_SERVICE_NAME);
        }
    }

    private static URI validateRelativePath(String relativePath) {
        try {
            URI uri = URI.create(relativePath);
            String rawPath = uri.getRawPath();
            String decodedPath = uri.getPath();
            String lowerRawPath = rawPath == null ? "" : rawPath.toLowerCase(Locale.ROOT);
            if (uri.isAbsolute()
                    || uri.getRawAuthority() != null
                    || rawPath == null
                    || !rawPath.startsWith("/")
                    || rawPath.startsWith("//")
                    || lowerRawPath.contains("%2e")
                    || containsParentSegment(decodedPath)
                    || rawPath.startsWith("/api/v1/uca/request")) {
                throw invalidPath();
            }
            return uri;
        } catch (UcaException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw invalidPath();
        }
    }

    private static boolean containsParentSegment(String path) {
        if (path == null) {
            return true;
        }
        for (String segment : path.split("/")) {
            if ("..".equals(segment)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, String> registrationMetadata() {
        Map<String, String> metadata = new LinkedHashMap<>(properties.getMetadata());
        if (!properties.getPrefix().isBlank()) {
            metadata.put(API_PREFIX_METADATA, properties.getPrefix());
        }
        return Map.copyOf(metadata);
    }

    private void updateRegistrationSecret(HttpHeaders headers) {
        properties.updateInternalSecret(headers.getFirst(REGISTRATION_SECRET_HEADER));
    }

    private static URI targetUri(UcaServiceInstance instance, String rawPath, String rawQuery) {
        URI baseUrl = instance.baseUrl();
        String scheme = baseUrl.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw invalidPath();
        }
        String apiPrefix = normalizeRegisteredApiPrefix(instance.metadata().get(API_PREFIX_METADATA));
        String targetPath = rawPath;
        if (!apiPrefix.isBlank() && !rawPath.equals(apiPrefix) && !rawPath.startsWith(apiPrefix + '/')) {
            targetPath = apiPrefix + rawPath;
        }
        String target = stripTrailingSlash(baseUrl.toString()) + targetPath;
        return URI.create(rawQuery == null || rawQuery.isBlank() ? target : target + '?' + rawQuery);
    }

    private static String normalizeRegisteredApiPrefix(String value) {
        if (value == null || value.isBlank() || "/".equals(value.trim())) {
            return "";
        }
        String normalized = value.trim();
        if (!normalized.startsWith("/")) {
            normalized = '/' + normalized;
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.contains("?")
                || normalized.contains("#")
                || normalized.contains("//")
                || normalized.contains("..")
                || normalized.toLowerCase(Locale.ROOT).contains("%2e")) {
            throw invalidPath();
        }
        return normalized;
    }

    private static String combineQuery(String first, String second) {
        if (first == null || first.isBlank()) {
            return second;
        }
        if (second == null || second.isBlank()) {
            return first;
        }
        return first + '&' + second;
    }

    private static boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof HttpTimeoutException || current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static double normalizeCpuLoad(double value) {
        return Double.isFinite(value) && value >= 0.0 ? Math.min(value, 1.0) : -1.0;
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static UcaException invalidPath() {
        return new UcaException(UcaResponseCode.UCA_INVALID_RELATIVE_PATH);
    }

    private static UcaException invalidResponse() {
        return new UcaException(UcaResponseCode.UCA_RESPONSE_INVALID);
    }

    public final class RequestSpec {

        private final HttpMethod method;
        private final String serviceName;
        private final String relativePath;
        private final HttpHeaders headers = new HttpHeaders();
        private Object body;

        private RequestSpec(HttpMethod method, String serviceName, String relativePath) {
            this.method = Objects.requireNonNull(method);
            this.serviceName = serviceName;
            this.relativePath = relativePath;
        }

        public RequestSpec header(String name, String value) {
            headers.set(name, value);
            return this;
        }

        public RequestSpec body(Object body) {
            this.body = body;
            if (headers.getContentType() == null) {
                headers.setContentType(MediaType.APPLICATION_JSON);
            }
            return this;
        }

        public <T> T retrieve(Class<T> responseType) {
            byte[] requestBody = serializeBody();
            byte[] responseBody = forward(method, serviceName, relativePath, null, headers, requestBody);
            try {
                JavaType resultType = objectMapper.getTypeFactory()
                        .constructParametricType(UcaResult.class, responseType);
                UcaResult<T> result = objectMapper.readValue(responseBody, resultType);
                if (result.code() != UcaResponseCode.SUCCESS.code()) {
                    throw new UcaException(result.code(), result.error(), result.message());
                }
                return result.data();
            } catch (UcaException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new UcaException(
                        UcaResponseCode.UCA_RESPONSE_INVALID,
                        "Unable to read target UCA response",
                        exception);
            }
        }

        private byte[] serializeBody() {
            if (body == null) {
                return new byte[0];
            }
            if (body instanceof byte[] bytes) {
                return bytes;
            }
            try {
                return objectMapper.writeValueAsBytes(body);
            } catch (Exception exception) {
                throw new UcaException(
                        UcaResponseCode.UCA_INVALID_REQUEST,
                        "Unable to serialize UCA request body",
                        exception);
            }
        }
    }

    private record TargetResponse(int httpStatus, byte[] body) {
    }

    private record RegistrationRequest(
            String applicationName,
            URI baseUrl,
            String version,
            Map<String, String> metadata) {
    }
}
