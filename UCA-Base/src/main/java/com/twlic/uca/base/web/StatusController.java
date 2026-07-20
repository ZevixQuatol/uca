package com.twlic.uca.base.web;

import java.util.List;

import com.twlic.uca.base.registry.InstanceRegistry;
import com.twlic.uca.base.web.dto.ApplicationResponse;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/applications")
public class StatusController {

    private static final String IDENTIFIER_PATTERN = "[A-Za-z0-9._-]+";

    private final InstanceRegistry registry;

    public StatusController(InstanceRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public List<ApplicationResponse> findAll() {
        return registry.findAll().stream().map(ApplicationResponse::from).toList();
    }

    @GetMapping("/{applicationCode}")
    public ApplicationResponse findApplication(
            @PathVariable @Pattern(regexp = IDENTIFIER_PATTERN) String applicationCode) {
        return ApplicationResponse.from(registry.findApplication(applicationCode));
    }
}
