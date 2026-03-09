package com.nexus.core.external.nibss;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.nibss")
record NibssProperties(String webhookSecret, boolean mockMode, String baseUrl) {}
