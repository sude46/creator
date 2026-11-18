package com.creator.common.web;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(HttpLimitProperties.class)
public class HttpLimitConfiguration {
}
