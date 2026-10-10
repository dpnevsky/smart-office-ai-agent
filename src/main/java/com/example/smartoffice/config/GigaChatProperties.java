package com.example.smartoffice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gigachat")
public record GigaChatProperties(
        String authKey,
        String model,
        boolean verifySsl,
        boolean logRequests,
        boolean logResponses
) {
}
