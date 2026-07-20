package com.twlic.uca.base.web;

import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

import com.twlic.uca.base.UcaBaseApplication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private static final String SYSTEM_NAME = "UCA-Base";
    private static final String FALLBACK_VERSION = "0.0.1";

    private final Clock clock;
    private final String applicationName;
    private final Instant startedAt;

    public DashboardController(
            Clock clock,
            @Value("${spring.application.name:uca-base}") String applicationName) {
        this.clock = clock;
        this.applicationName = applicationName;
        this.startedAt = Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime());
    }

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model) {
        model.addAttribute("systemName", SYSTEM_NAME);
        model.addAttribute("applicationName", applicationName);
        model.addAttribute("version", applicationVersion());
        model.addAttribute("javaVersion", System.getProperty("java.version"));
        model.addAttribute("startedAt", DateTimeFormatter.ISO_INSTANT.format(startedAt));
        model.addAttribute("renderedAt", DateTimeFormatter.ISO_INSTANT.format(clock.instant()));
        return "dashboard";
    }

    private String applicationVersion() {
        String version = UcaBaseApplication.class.getPackage().getImplementationVersion();
        return version == null || version.isBlank() ? FALLBACK_VERSION : version;
    }
}
