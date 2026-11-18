package com.streamly.common;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("streamly")
public record StreamlyProperties(
    String demoPassword,
    Duration publishTokenTtl,
    Duration reconnectGrace,
    String mediamtxApiUrl) {}
