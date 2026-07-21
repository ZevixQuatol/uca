package com.twlic.uca.client.core;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

final class UcaServiceCallInterceptor implements HandlerInterceptor {

    private final UcaServiceSignature signature;

    UcaServiceCallInterceptor(UcaServiceSignature signature) {
        this.signature = signature;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        boolean responseEndpoint =
                AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), UCAResponse.class)
                        || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), UCAResponse.class);
        boolean serviceCall = signature.isServiceCall(request);

        if (serviceCall && !responseEndpoint) {
            throw new UcaException(UcaResponseCode.UCA_ENDPOINT_NOT_EXPOSED);
        }
        if (responseEndpoint && !serviceCall) {
            throw new UcaException(UcaResponseCode.UCA_SERVICE_AUTH_REQUIRED);
        }
        if (responseEndpoint) {
            byte[] body = Optional.ofNullable((byte[]) request.getAttribute(UcaSignedRequestBodyFilter.BODY_ATTRIBUTE))
                    .orElseGet(() -> new byte[0]);
            signature.verify(request, body);
        }
        return true;
    }
}
