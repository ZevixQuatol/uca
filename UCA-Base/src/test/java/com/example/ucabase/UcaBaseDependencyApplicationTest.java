package com.example.ucabase;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
        classes = UcaBaseDependencyApplicationTest.ExternalApplication.class,
        properties = {
                "uca.base.scan-interval=1h",
                "uca.base.secret-interval=1h"
        })
@AutoConfigureMockMvc
class UcaBaseDependencyApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void dependencyAutoConfiguresBaseCapabilities() throws Exception {
        mockMvc.perform(post("/api/v1/applications/external-app/instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "applicationName":"External App",
                                  "baseUrl":"http://127.0.0.1:29001",
                                  "version":"1.0.0",
                                  "metadata":{}
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.applicationCode").value("external-app"))
                .andExpect(jsonPath("$.instanceId").isNotEmpty());
    }

    @SpringBootApplication
    static class ExternalApplication {
    }
}
