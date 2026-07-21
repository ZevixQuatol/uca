package com.twlic.uca.base.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.twlic.uca.base.UcaBaseApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = UcaBaseApplication.StandaloneConfiguration.class)
@AutoConfigureMockMvc
class DashboardPageTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rootRendersTheFreemarkerDashboard() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(view().name("dashboard"))
                .andExpect(model().attribute("systemName", "UCA-Base"))
                .andExpect(model().attribute("applicationName", "uca-base"))
                .andExpect(model().attributeExists("version", "javaVersion", "startedAt", "renderedAt"))
                .andExpect(content().string(containsString("系统运行与子业务注册总览")))
                .andExpect(content().string(containsString("/css/dashboard.css")))
                .andExpect(content().string(containsString("/js/dashboard.js")));
    }

    @Test
    void dashboardAliasRendersTheSameView() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"));
    }

    @Test
    void dashboardStaticAssetsAreAvailable() throws Exception {
        mockMvc.perform(get("/css/dashboard.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("--navy")));

        mockMvc.perform(get("/js/dashboard.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/actuator/health")))
                .andExpect(content().string(containsString("/api/v1/applications")));
    }
}
