package dev.univer.shmel.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "bot")
@Getter @Setter
public class TelegramProperties {
    private String username;
    private String token;
    private String summaryTime;
    private String defaultZoneId;
}
