package com.twlic.uca.base.web;

import java.util.UUID;

import com.twlic.uca.base.registry.InstanceRegistry;
import com.twlic.uca.base.security.UcaSecretManager;
import com.twlic.uca.base.web.dto.InstanceResponse;
import com.twlic.uca.base.web.dto.RegisterInstanceRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    private final UcaSecretManager secretManager;

    public RegistrationController(InstanceRegistry registry, UcaSecretManager secretManager) {
        this.registry = registry;
        this.secretManager = secretManager;
    }

    @PostMapping
    public ResponseEntity<InstanceResponse> register(
            @PathVariable @Pattern(regexp = IDENTIFIER_PATTERN) String applicationCode,
            @Valid @RequestBody RegisterInstanceRequest request) {
        String instanceId = UUID.randomUUID().toString();
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(UcaSecretManager.SECRET_HEADER, secretManager.current())
                .body(InstanceResponse.from(
                        registry.register(request.toRegistration(applicationCode, instanceId)).instance()));
    }

    @PutMapping("/{instanceId}/heartbeat")
    public ResponseEntity<InstanceResponse> heartbeat(
            @PathVariable @Pattern(regexp = IDENTIFIER_PATTERN) String applicationCode,
            @PathVariable @Pattern(regexp = IDENTIFIER_PATTERN) String instanceId) {
        return ResponseEntity.ok()
                .header(UcaSecretManager.SECRET_HEADER, secretManager.current())
                .body(InstanceResponse.from(registry.heartbeat(applicationCode, instanceId)));
    }

    @DeleteMapping("/{instanceId}")
    public ResponseEntity<Void> deregister(
            @PathVariable @Pattern(regexp = IDENTIFIER_PATTERN) String applicationCode,
            @PathVariable @Pattern(regexp = IDENTIFIER_PATTERN) String instanceId) {
        registry.deregister(applicationCode, instanceId);
        return ResponseEntity.noContent().build();
    }
}
