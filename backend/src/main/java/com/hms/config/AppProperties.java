package com.hms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Cors cors, Security security, Mail mail, Storage storage) {
    public record Cors(String allowedOrigin) {}
    public record Security(String jwtSecret, int accessTokenMinutes, int refreshTokenDays,
                           int lockoutMinutes, int maxFailedAttempts) {}
    public record Mail(String from, String resetUrl) {}
    public record Storage(String uploadDir) {}
}
