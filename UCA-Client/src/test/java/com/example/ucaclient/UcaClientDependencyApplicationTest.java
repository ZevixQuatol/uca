package com.example.ucaclient;

import static org.assertj.core.api.Assertions.assertThat;

import com.twlic.uca.client.core.UcaClient;
import com.twlic.uca.client.core.UcaClientProperties;
import com.twlic.uca.client.core.UcaRequestEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        classes = UcaClientDependencyApplicationTest.ExternalApplication.class,
        properties = {
                "uca.base.port=9",
                "uca.client.host=127.0.0.1:29001",
                "uca.client.code=external-client",
                "uca.client.name=External Client",
                "uca.client.interval=1h",
                "uca.client.connect-timeout=100ms"
        })
class UcaClientDependencyApplicationTest {

    @Autowired
    private UcaClient client;

    @Autowired
    private UcaRequestEndpoint requestEndpoint;

    @Autowired
    private UcaClientProperties properties;

    @Test
    void dependencyAutoConfiguresClientCapabilities() {
        assertThat(client).isNotNull();
        assertThat(requestEndpoint).isNotNull();
        assertThat(properties.getCode()).isEqualTo("external-client");
    }

    @SpringBootApplication
    static class ExternalApplication {
    }
}
