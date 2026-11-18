package lk.hms.modules.analytics;

import lk.hms.common.time.HospitalTime;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * SQL helpers shared by the reports. Timestamps are stored in UTC; grouping happens on the
 * hospital-local value via CONVERT_TZ with a fixed offset (no MySQL time-zone tables needed).
 */
@Component
public class Sql {

    final NamedParameterJdbcTemplate jdbc;
    private final HospitalTime time;

    public Sql(NamedParameterJdbcTemplate jdbc, HospitalTime time) {
        this.jdbc = jdbc;
        this.time = time;
    }

    // shortcut: one offset for the whole range; fine for Asia/Colombo (no DST), revisit for a DST zone
    String offset() {
        return time.zone().getRules().getOffset(time.now()).getId().replace("Z", "+00:00");
    }

    /** Hospital-local expression for a UTC column. */
    String local(String column) {
        return "CONVERT_TZ(" + column + ", '+00:00', '" + offset() + "')";
    }

    /** Bucket start date (yyyy-MM-dd) of a UTC column for the filter's granularity. */
    String bucket(String column, Filter f) {
        String d = "DATE(" + local(column) + ")";
        return switch (f.granularity()) {
            case "week" -> "DATE_FORMAT(" + d + " - INTERVAL WEEKDAY(" + d + ") DAY, '%Y-%m-%d')";
            case "month" -> "DATE_FORMAT(" + d + ", '%Y-%m-01')";
            default -> "DATE_FORMAT(" + d + ", '%Y-%m-%d')";
        };
    }

    /** Same, for a column that is already a local DATE (attendance.work_date). */
    String dateBucket(String column, Filter f) {
        return switch (f.granularity()) {
            case "week" -> "DATE_FORMAT(" + column + " - INTERVAL WEEKDAY(" + column + ") DAY, '%Y-%m-%d')";
            case "month" -> "DATE_FORMAT(" + column + ", '%Y-%m-01')";
            default -> "DATE_FORMAT(" + column + ", '%Y-%m-%d')";
        };
    }

    /** :from and :to as UTC instants bounding the local date range [from, to + 1 day). */
    MapSqlParameterSource params(Filter f) {
        return new MapSqlParameterSource()
                .addValue("from", utc(f.from().atStartOfDay()))
                .addValue("to", utc(f.to().plusDays(1).atStartOfDay()))
                .addValue("fromDate", f.from())
                .addValue("toDate", f.to())
                .addValue("dept", f.departmentId())
                .addValue("now", utc(LocalDateTime.ofInstant(time.now(), time.zone())))
                .addValue("today", time.today());
    }

    LocalDateTime utc(LocalDateTime local) {
        return local.atZone(time.zone()).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    LocalDate today() {
        return time.today();
    }

    BigDecimal number(String sql, MapSqlParameterSource p) {
        BigDecimal v = jdbc.queryForObject(sql, p, BigDecimal.class);
        return v == null ? BigDecimal.ZERO : v;
    }

    /** Rows of (label, value). */
    List<Report.Row> rows(String sql, MapSqlParameterSource p) {
        return jdbc.query(sql, p, (rs, i) -> new Report.Row(rs.getString(1), nz(rs.getBigDecimal(2))));
    }

    /** A series of (bucket, value) with every bucket in the range present, zero where there was no data. */
    List<Report.Point> series(String sql, MapSqlParameterSource p, Filter f) {
        Map<String, BigDecimal> found = new HashMap<>();
        jdbc.query(sql, p, rs -> {
            found.put(rs.getString(1), nz(rs.getBigDecimal(2)));
        });
        List<Report.Point> out = new ArrayList<>();
        for (LocalDate b : buckets(f)) {
            out.add(new Report.Point(b.toString(), found.getOrDefault(b.toString(), BigDecimal.ZERO)));
        }
        return out;
    }

    static List<LocalDate> buckets(Filter f) {
        List<LocalDate> out = new ArrayList<>();
        LocalDate b = switch (f.granularity()) {
            case "week" -> f.from().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case "month" -> f.from().withDayOfMonth(1);
            default -> f.from();
        };
        while (!b.isAfter(f.to())) {
            out.add(b);
            b = switch (f.granularity()) {
                case "week" -> b.plusWeeks(1);
                case "month" -> b.plusMonths(1);
                default -> b.plusDays(1);
            };
        }
        return out;
    }

    static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** part / whole as a percentage with one decimal; 0 when whole is 0. */
    static BigDecimal pct(BigDecimal part, BigDecimal whole) {
        return whole.signum() == 0 ? BigDecimal.ZERO : part.multiply(BigDecimal.valueOf(100)).divide(whole, 1, RoundingMode.HALF_UP);
    }

    static BigDecimal round(BigDecimal v, int scale) {
        return v.setScale(scale, RoundingMode.HALF_UP);
    }
}
