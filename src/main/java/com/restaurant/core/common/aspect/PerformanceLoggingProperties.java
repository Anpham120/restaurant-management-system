package com.restaurant.core.common.aspect;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Ngưỡng coi một lời gọi Service là chậm (biến môi trường SLOW_SERVICE_MS, mặc định 500 ms). */
@ConfigurationProperties("app.logging")
public record PerformanceLoggingProperties(@DefaultValue("500") long slowServiceMs) {
}
