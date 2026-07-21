package com.twlic.uca.client.core;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

final class UcaSignedRequestBodyFilter extends OncePerRequestFilter {

    static final String BODY_ATTRIBUTE = UcaSignedRequestBodyFilter.class.getName() + ".body";

    private final UcaClientProperties properties;
    private final ObjectMapper objectMapper;

    UcaSignedRequestBodyFilter(UcaClientProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getHeader(UcaServiceSignature.SIGNATURE) == null;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        byte[] body = request.getInputStream().readNBytes(properties.getMaxBodyBytes() + 1);
        if (body.length > properties.getMaxBodyBytes()) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            String requestId = request.getHeader(UcaServiceSignature.REQUEST_ID);
            UcaException exception = new UcaException(
                    UcaResponseCode.UCA_INVALID_REQUEST,
                    "UCA request body is too large");
            objectMapper.writeValue(
                    response.getOutputStream(),
                    UcaResult.failure(exception, requestId == null ? UUID.randomUUID().toString() : requestId));
            return;
        }

        CachedBodyRequest wrapped = new CachedBodyRequest(request, body);
        wrapped.setAttribute(BODY_ATTRIBUTE, body);
        filterChain.doFilter(wrapped, response);
    }

    // ponytail: signed REST bodies are buffered; add streaming signatures when file relay is required.
    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return input.read();
                }

                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    // Synchronous MVC requests do not use an asynchronous read listener.
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }
}
