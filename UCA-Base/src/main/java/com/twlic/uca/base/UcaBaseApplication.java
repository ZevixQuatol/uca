package com.twlic.uca.base;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;

public class UcaBaseApplication {

    public static void main(String[] args) {
        SpringApplication.run(StandaloneConfiguration.class, args);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    public static class StandaloneConfiguration {
    }
}
