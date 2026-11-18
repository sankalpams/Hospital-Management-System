package lk.hms.common.settings;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Reads runtime-tunable values from {@code system_settings}. Read on each use (the table is tiny
 * and indexed by primary key) so an admin change takes effect without a restart.
 */
@Service
public class SettingsService {

    private final JdbcTemplate jdbc;

    public SettingsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int getInt(String key, int defaultValue) {
        String value = get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public String getString(String key, String defaultValue) {
        String value = get(key);
        return value == null ? defaultValue : value;
    }

    private String get(String key) {
        List<String> rows = jdbc.queryForList(
                "SELECT setting_value FROM system_settings WHERE setting_key = ?", String.class, key);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
