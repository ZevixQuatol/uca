package com.twlic.uca.base.web.dto;

import java.util.List;

import com.twlic.uca.base.registry.ApplicationSnapshot;

public record ApplicationResponse(
        String applicationCode,
        String applicationName,
        List<InstanceResponse> instances) {

    public static ApplicationResponse from(ApplicationSnapshot application) {
        return new ApplicationResponse(
                application.applicationCode(),
                application.applicationName(),
                application.instances().stream().map(InstanceResponse::from).toList());
    }
}
