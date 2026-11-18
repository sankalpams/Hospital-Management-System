package lk.hms.common.time;

import lk.hms.common.settings.SettingsService;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Converts between the hospital's local time (system_settings hospital.timezone, default
 * Asia/Colombo) and the UTC values stored in the database. "Today", schedule hours and slot
 * labels are local; persisted DATETIME columns are UTC.
 */
@Component
public class HospitalTime {

    private final SettingsService settings;
    private final Clock clock;

    public HospitalTime(SettingsService settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
    }

    public ZoneId zone() {
        try {
            return ZoneId.of(settings.getString("hospital.timezone", "Asia/Colombo"));
        } catch (RuntimeException e) {
            return ZoneId.of("Asia/Colombo");
        }
    }

    public Instant now() {
        return Instant.now(clock);
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(zone()));
    }

    /** Local wall-clock time in the hospital -> UTC LocalDateTime for a DATETIME column. */
    public LocalDateTime toUtc(LocalDateTime local) {
        return local.atZone(zone()).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /** UTC DATETIME value -> hospital local wall-clock time. */
    public LocalDateTime toLocal(LocalDateTime utc) {
        return utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone()).toLocalDateTime();
    }

    public Instant toInstant(LocalDateTime utc) {
        return utc.toInstant(ZoneOffset.UTC);
    }
}
