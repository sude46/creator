package com.creator.common.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("creator.http")
public record HttpLimitProperties(@Min(1024) @Max(10485760) long maxJsonBytes) {
}
