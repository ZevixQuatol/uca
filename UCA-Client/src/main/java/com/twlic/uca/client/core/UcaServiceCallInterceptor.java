package com.twlic.uca.client.core;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

final class UcaServiceCallInterceptor implements HandlerInterceptor {

    private final UcaServiceSignature signature;
    private final UcaClientProperties properties;

    UcaServiceCallInterceptor(UcaServiceSignature signature, UcaClientProperties properties) {
        this.signature = signature;
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        boolean annotatedEndpoint =
                AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), UCAResponse.class)
                        || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), UCAResponse.class);
        boolean serviceCall = signature.isServiceCall(request);
        boolean exposedEndpoint = annotatedEndpoint
                || properties.getMode() == UcaClientProperties.Mode.FULL;

        if (serviceCall && !exposedEndpoint) {
            throw new UcaException(UcaResponseCode.UCA_ENDPOINT_NOT_EXPOSED);
        }
        if (annotatedEndpoint && !serviceCall) {
            throw new UcaException(UcaResponseCode.UCA_SERVICE_AUTH_REQUIRED);
        }
        if (serviceCall) {
            byte[] body = Optional.ofNullable((byte[]) request.getAttribute(UcaSignedRequestBodyFilter.BODY_ATTRIBUTE))
                    .orElseGet(() -> new byte[0]);
            signature.verify(request, body);
        }
        return true;
    }
}
