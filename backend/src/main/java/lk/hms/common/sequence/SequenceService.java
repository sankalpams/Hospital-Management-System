package lk.hms.common.sequence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gap-free, human-facing numbers (MRN, appointment no, invoice no ...).
 * <p>
 * The upsert row-locks the sequence row until the caller's transaction ends, so concurrent
 * callers queue and a rolled-back transaction gives its number back. LAST_INSERT_ID(expr) is
 * connection-scoped, so reading it right after on the same transaction connection is safe.
 */
@Service
public class SequenceService {

    private final JdbcTemplate jdbc;

    public SequenceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Returns the next value for {@code key}, starting at 1. Must run inside the business transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public long next(String key) {
        jdbc.update("""
                INSERT INTO sequences (seq_key, next_value) VALUES (?, LAST_INSERT_ID(2))
                ON DUPLICATE KEY UPDATE next_value = LAST_INSERT_ID(next_value + 1)
                """, key);
        Long nextFree = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return nextFree - 1;
    }

    /** e.g. nextPlain("EMP", 6) -> "EMP-000042" (one series for all years) */
    public String nextPlain(String prefix, int digits) {
        return String.format("%s-%0" + digits + "d", prefix, next(prefix));
    }

    /** e.g. nextFormatted("HMS", 2026, 6) -> "HMS-2026-000042" (series restarts each year) */
    public String nextFormatted(String prefix, int year, int digits) {
        long value = next(prefix + "-" + year);
        return String.format("%s-%d-%0" + digits + "d", prefix, year, value);
    }
}
