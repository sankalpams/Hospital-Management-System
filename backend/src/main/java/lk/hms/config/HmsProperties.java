package lk.hms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

/** Typed view of the {@code hms.*} configuration block. Values come from environment variables. */
@ConfigurationProperties(prefix = "hms")
public record HmsProperties(String corsOrigins, String uploadDir, BootstrapAdmin bootstrapAdmin) {

    public List<String> corsOriginList() {
        if (corsOrigins == null || corsOrigins.isBlank()) {
            return List.of();
        }
        return Arrays.stream(corsOrigins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public record BootstrapAdmin(String username, String password, String fullName) {
        public boolean isConfigured() {
            return username != null && !username.isBlank() && password != null && !password.isBlank();
        }
    }
}
