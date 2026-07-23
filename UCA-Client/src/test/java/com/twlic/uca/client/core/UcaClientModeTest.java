package com.twlic.uca.client.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import tools.jackson.databind.ObjectMapper;

class UcaClientModeTest {

    private static final String PATH = "/api/v1/existing/ping";
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-07-21T09:00:00Z"), ZoneOffset.UTC);

    @Test
    void partialModeRemainsTheDefaultAndRejectsUnannotatedServiceCalls() throws Exception {
        UcaClientProperties properties = properties(UcaClientProperties.Mode.PARTIAL);
        UcaServiceSignature signature = new UcaServiceSignature(properties, CLOCK);
        UcaServiceCallInterceptor interceptor = new UcaServiceCallInterceptor(signature, properties);

        assertThat(new UcaClientProperties().getMode()).isEqualTo(UcaClientProperties.Mode.PARTIAL);
        assertThatThrownBy(() -> interceptor.preHandle(
                        signedRequest(signature),
                        new MockHttpServletResponse(),
                        plainHandler()))
                .isInstanceOfSatisfying(UcaException.class, exception ->
                        assertThat(exception.code()).isEqualTo(UcaResponseCode.UCA_ENDPOINT_NOT_EXPOSED.code()));
    }

    @Test
    void fullModeAcceptsSignedCallsWithoutChangingOrdinaryRequests() throws Exception {
        UcaClientProperties properties = properties(UcaClientProperties.Mode.FULL);
        UcaServiceSignature signature = new UcaServiceSignature(properties, CLOCK);
        UcaServiceCallInterceptor interceptor = new UcaServiceCallInterceptor(signature, properties);

        assertThat(interceptor.preHandle(
                signedRequest(signature),
                new MockHttpServletResponse(),
                plainHandler())).isTrue();
        assertThat(interceptor.preHandle(
                new MockHttpServletRequest("GET", PATH),
                new MockHttpServletResponse(),
                plainHandler())).isTrue();
    }

    @Test
    void fullModeWrapsOnlyServiceCallResponses() throws Exception {
        UcaClientProperties properties = properties(UcaClientProperties.Mode.FULL);
        UcaResponseAdvice advice = new UcaResponseAdvice(new ObjectMapper(), properties);
        MethodParameter returnType = new MethodParameter(plainMethod(), -1);
        Map<String, String> body = Map.of("message", "pong");

        MockHttpServletRequest ordinaryRequest = new MockHttpServletRequest("GET", PATH);
        MockHttpServletResponse ordinaryResponse = new MockHttpServletResponse();
        ordinaryResponse.setStatus(201);
        Object ordinaryBody = advice.beforeBodyWrite(
                body,
                returnType,
                MediaType.APPLICATION_JSON,
                ByteArrayHttpMessageConverter.class,
                new ServletServerHttpRequest(ordinaryRequest),
                new ServletServerHttpResponse(ordinaryResponse));

        MockHttpServletRequest serviceRequest = new MockHttpServletRequest("GET", PATH);
        serviceRequest.addHeader(UcaServiceSignature.SIGNATURE, "verified-by-interceptor");
        Object serviceBody = advice.beforeBodyWrite(
                body,
                returnType,
                MediaType.APPLICATION_JSON,
                ByteArrayHttpMessageConverter.class,
                new ServletServerHttpRequest(serviceRequest),
                new ServletServerHttpResponse(new MockHttpServletResponse()));

        assertThat(advice.supports(returnType, ByteArrayHttpMessageConverter.class)).isTrue();
        assertThat(ordinaryBody).isSameAs(body);
        assertThat(ordinaryResponse.getStatus()).isEqualTo(201);
        assertThat(serviceBody)
                .isInstanceOfSatisfying(UcaResult.class, result -> {
                    assertThat(result.code()).isZero();
                    assertThat(result.data()).isEqualTo(body);
                });
    }

    private static UcaClientProperties properties(UcaClientProperties.Mode mode) {
        UcaClientProperties properties = new UcaClientProperties();
        properties.setCode("existing-service");
        properties.setInternalSecret("test-secret");
        properties.assignInstanceId("existing-instance");
        properties.setMode(mode);
        return properties;
    }

    private static MockHttpServletRequest signedRequest(UcaServiceSignature signature) {
        HttpHeaders headers = new HttpHeaders();
        signature.sign(headers, HttpMethod.GET, PATH, null, new byte[0], "request-1");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", PATH);
        headers.forEach((name, values) -> values.forEach(value -> request.addHeader(name, value)));
        return request;
    }

    private static HandlerMethod plainHandler() throws NoSuchMethodException {
        return new HandlerMethod(new ExistingController(), plainMethod());
    }

    private static Method plainMethod() throws NoSuchMethodException {
        return ExistingController.class.getDeclaredMethod("ping");
    }

    private static final class ExistingController {

        public Map<String, String> ping() {
            return Map.of("message", "pong");
        }
    }
}
