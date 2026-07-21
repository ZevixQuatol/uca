package com.twlic.uca.client.core;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@UCARequest
@RestController
@RequestMapping("/api/v1/uca/request")
public class UcaRequestEndpoint {

    private final UcaClient client;

    public UcaRequestEndpoint(UcaClient client) {
        this.client = client;
    }

    @GetMapping("/services")
    public UcaResult<List<String>> services(
            @RequestHeader(value = UcaServiceSignature.REQUEST_ID, required = false) String requestId) {

        return UcaResult.success(
                client.availableServiceNames(),
                requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId);
    }

    @RequestMapping("/{serviceName}/{*relativePath}")
    public ResponseEntity<byte[]> forward(
            @PathVariable String serviceName,
            @PathVariable String relativePath,
            @RequestHeader HttpHeaders headers,
            @RequestBody(required = false) byte[] body,
            HttpServletRequest request) {

        byte[] response = client.forward(
                HttpMethod.valueOf(request.getMethod()),
                serviceName,
                relativePath,
                request.getQueryString(),
                headers,
                body);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }
}
