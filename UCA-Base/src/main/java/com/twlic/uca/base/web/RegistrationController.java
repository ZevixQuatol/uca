package com.twlic.uca.base.web;

import com.twlic.uca.base.registry.InstanceRegistry;
import com.twlic.uca.base.registry.RegistrationResult;
import com.twlic.uca.base.web.dto.InstanceResponse;
import com.twlic.uca.base.web.dto.RegisterInstanceRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/applications/{applicationCode}/instances")
public class RegistrationController {

    private static final String IDENTIFIER_PATTERN = "[A-Za-z0-9._-]+";

    private final InstanceRegistry registry;

    public RegistrationController(InstanceRegistry registry) {
        this.registry = registry;
    }

    @PutMapping("/{instanceId}")
    public ResponseEntity<InstanceResponse> register(
            @PathVariable @Pattern(regexp = IDENTIFIER_PATTERN) String applicationCode,
            @PathVariable @Pattern(regexp = IDENTIFIER_PATTERN) String instanceId,
            @Valid @RequestBody RegisterInstanceRequest request) {
        RegistrationResult result = registry.register(request.toRegistration(applicationCode, instanceId));
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(InstanceResponse.from(result.instance()));
    }

    @PutMapping("/{instanceId}/heartbeat")
    public InstanceResponse heartbeat(
            @PathVariable @Pattern(regexp = IDENTIFIER_PATTERN) String applicationCode,
            @PathVariable @Pattern(regexp = IDENTIFIER_PATTERN) String instanceId) {
        return InstanceResponse.from(registry.heartbeat(applicationCode, instanceId));
    }

    @DeleteMapping("/{instanceId}")
    public ResponseEntity<Void> deregister(
            @PathVariable @Pattern(regexp = IDENTIFIER_PATTERN) String applicationCode,
            @PathVariable @Pattern(regexp = IDENTIFIER_PATTERN) String instanceId) {
        registry.deregister(applicationCode, instanceId);
        return ResponseEntity.noContent().build();
    }
}
