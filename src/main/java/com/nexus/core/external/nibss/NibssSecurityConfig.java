package com.nexus.core.external.nibss;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
class NibssSecurityConfig {

    @Value("${app.nibss.allowed-ips:*}")
    private List<String> allowedIps;

    @Bean
    FilterRegistrationBean<NibssIpAllowlistFilter> nibssIpAllowlistFilter() {
        FilterRegistrationBean<NibssIpAllowlistFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new NibssIpAllowlistFilter(allowedIps));
        registration.addUrlPatterns("/api/webhooks/nibss/*");
        registration.setOrder(1);
        return registration;
    }
}
