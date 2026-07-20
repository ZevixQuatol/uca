package com.twlic.uca.base.web;

import com.twlic.uca.base.registry.InstanceRegistry;
import com.twlic.uca.base.web.dto.InstanceResponse;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/applications")
public class DiscoveryController {

    private static final String IDENTIFIER_PATTERN = "[A-Za-z0-9._-]+";

    private final InstanceRegistry registry;

    public DiscoveryController(InstanceRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/{applicationCode}/instances/next")
    public InstanceResponse discover(
            @PathVariable @Pattern(regexp = IDENTIFIER_PATTERN) String applicationCode) {
        return InstanceResponse.from(registry.discover(applicationCode));
    }
}
