package com.twlic.uca.client.core;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@RestControllerAdvice
public class UcaResponseAdvice implements ResponseBodyAdvice<Object> {

    private final ObjectMapper objectMapper;

    public UcaResponseAdvice(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(
            MethodParameter returnType,
            Class<? extends HttpMessageConverter<?>> converterType) {

        return (returnType.getMethod() != null
                && AnnotatedElementUtils.hasAnnotation(returnType.getMethod(), UCAResponse.class))
                || AnnotatedElementUtils.hasAnnotation(returnType.getContainingClass(), UCAResponse.class);
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {

        response.setStatusCode(HttpStatus.OK);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        Object result = body instanceof UcaResult<?>
                ? body
                : UcaResult.success(body, requestId(request.getHeaders().getFirst(UcaServiceSignature.REQUEST_ID)));
        if (StringHttpMessageConverter.class.isAssignableFrom(selectedConverterType)) {
            try {
                return objectMapper.writeValueAsString(result);
            } catch (JacksonException exception) {
                throw new UcaException(
                        UcaResponseCode.UCA_INTERNAL_ERROR,
                        "Unable to serialize UCA response",
                        exception);
            }
        }
        return result;
    }

    @ExceptionHandler(UcaException.class)
    public ResponseEntity<UcaResult<Void>> handleUcaException(
            UcaException exception,
            HttpServletRequest request) {

        return ResponseEntity.ok(UcaResult.failure(
                exception,
                requestId(request.getHeader(UcaServiceSignature.REQUEST_ID))));
    }

    private static String requestId(String value) {
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }
}
