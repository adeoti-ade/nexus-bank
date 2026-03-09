package com.nexus.core.external.nibss;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(NibssProperties.class)
class NibssWebhookConfig {}
