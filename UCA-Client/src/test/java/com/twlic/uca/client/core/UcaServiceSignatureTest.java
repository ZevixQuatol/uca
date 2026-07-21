package com.twlic.uca.client.core;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;

class UcaServiceSignatureTest {

    @Test
    void acceptsThePreviousSecretDuringRotation() {
        UcaClientProperties properties = new UcaClientProperties();
        properties.setCode("store");
        properties.assignInstanceId("store-1");
        properties.setInternalSecret("old-secret");
        UcaServiceSignature signature = new UcaServiceSignature(
                properties,
                Clock.fixed(Instant.parse("2026-07-21T00:00:00Z"), ZoneOffset.UTC));
        HttpHeaders headers = new HttpHeaders();
        signature.sign(headers, HttpMethod.GET, "/api/v1/audit/ping", null, new byte[0], "request-1");

        properties.setInternalSecret("new-secret");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/audit/ping");
        headers.forEach((name, values) -> values.forEach(value -> request.addHeader(name, value)));

        assertThatCode(() -> signature.verify(request, new byte[0])).doesNotThrowAnyException();
    }
}
