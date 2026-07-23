package com.twlic.uca.client.core;

import java.net.URI;
import java.time.Clock;

import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@EnableScheduling
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(UcaClientProperties.class)
public class UcaClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @ConditionalOnMissingBean
    Clock ucaClientClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    UcaServiceSignature ucaServiceSignature(UcaClientProperties properties, Clock clock) {
        return new UcaServiceSignature(properties, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    UcaClient ucaClient(
            UcaClientProperties properties,
            RestClient.Builder builder,
            Clock clock,
            ObjectMapper objectMapper,
            UcaServiceSignature signature,
            @Value("${uca.base.host:127.0.0.1}") String baseHost,
            @Value("${uca.base.port:20000}") int basePort) {
        return new UcaClient(
                URI.create("http://" + baseHost + ':' + basePort),
                properties,
                builder,
                clock,
                objectMapper,
                signature);
    }

    @Bean
    @ConditionalOnMissingBean
    RegistrationLifecycle registrationLifecycle(
            UcaClient client,
            UcaClientProperties properties) {
        return new RegistrationLifecycle(client, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    UcaClientEndpoint ucaClientEndpoint(
            UcaClient client,
            UcaClientProperties properties,
            RegistrationLifecycle lifecycle) {
        return new UcaClientEndpoint(client, properties, lifecycle);
    }

    @Bean
    @ConditionalOnMissingBean
    UcaSignedRequestBodyFilter ucaSignedRequestBodyFilter(
            UcaClientProperties properties,
            ObjectMapper objectMapper) {
        return new UcaSignedRequestBodyFilter(properties, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    UcaServiceCallInterceptor ucaServiceCallInterceptor(
            UcaServiceSignature signature,
            UcaClientProperties properties) {
        return new UcaServiceCallInterceptor(signature, properties);
    }

    @Bean
    WebMvcConfigurer ucaWebMvcConfigurer(UcaServiceCallInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    UcaResponseAdvice ucaResponseAdvice(
            ObjectMapper objectMapper,
            UcaClientProperties properties) {
        return new UcaResponseAdvice(objectMapper, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    UcaRequestEndpoint ucaRequestEndpoint(UcaClient client) {
        return new UcaRequestEndpoint(client);
    }
}
