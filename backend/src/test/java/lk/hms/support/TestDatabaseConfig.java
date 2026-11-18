package lk.hms.support;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.sql.Connection;
import java.sql.SQLException;

/** Fresh schema for every test run (clean + migrate) and a controllable clock. */
@TestConfiguration
public class TestDatabaseConfig {

    @Bean
    FlywayMigrationStrategy cleanMigrate() {
        return flyway -> {
            String schema;
            try (Connection c = flyway.getConfiguration().getDataSource().getConnection()) {
                schema = c.getCatalog();
            } catch (SQLException e) {
                throw new IllegalStateException("Cannot inspect test database", e);
            }
            if (!"hms_test".equals(schema)) {
                throw new IllegalStateException("Refusing to clean a non-test database schema: " + schema);
            }
            flyway.clean();
            flyway.migrate();
            // every MockMvc request comes from 127.0.0.1; tests that need the per-IP limit set their own
            try (Connection c = flyway.getConfiguration().getDataSource().getConnection();
                 var st = c.createStatement()) {
                st.executeUpdate("UPDATE system_settings SET setting_value = '1000000' WHERE setting_key = 'security.ip_max_failed_logins'");
            } catch (SQLException e) {
                throw new IllegalStateException("Cannot relax the per-IP limit for tests", e);
            }
        };
    }

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock();
    }
}
