package com.twlic.uca.base.autoconfigure;

import java.time.Clock;

import com.twlic.uca.base.config.RegistryProperties;
import com.twlic.uca.base.lifecycle.InstanceLifecycleScheduler;
import com.twlic.uca.base.registry.InstanceRegistry;
import com.twlic.uca.base.security.UcaSecretManager;
import com.twlic.uca.base.web.ApiExceptionHandler;
import com.twlic.uca.base.web.DashboardController;
import com.twlic.uca.base.web.DiscoveryController;
import com.twlic.uca.base.web.RegistrationController;
import com.twlic.uca.base.web.StatusController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(RegistryProperties.class)
@Import({
        InstanceRegistry.class,
        InstanceLifecycleScheduler.class,
        UcaSecretManager.class,
        ApiExceptionHandler.class,
        DashboardController.class,
        DiscoveryController.class,
        RegistrationController.class,
        StatusController.class
})
public class UcaBaseAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock ucaBaseClock() {
        return Clock.systemUTC();
    }
}
