package lk.hms.modules.analytics;

import lk.hms.common.exception.BusinessException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * Report filter in hospital-local dates (both ends inclusive). Granularity defaults from the span:
 * up to 31 days by day, up to 6 months by week, otherwise by month.
 */
public record Filter(LocalDate from, LocalDate to, Long departmentId, String granularity) {

    public static Filter of(LocalDate from, LocalDate to, Long departmentId, String granularity, LocalDate today) {
        LocalDate t = to == null ? today : to;
        LocalDate f = from == null ? t.minusDays(29) : from;
        if (f.isAfter(t)) {
            throw BusinessException.badRequest("INVALID_RANGE", "The start date must be on or before the end date.");
        }
        long days = ChronoUnit.DAYS.between(f, t) + 1;
        if (days > 731) {
            throw BusinessException.badRequest("INVALID_RANGE", "Choose a range of up to two years.");
        }
        String g = granularity == null || granularity.isBlank()
                ? (days <= 31 ? "day" : days <= 183 ? "week" : "month")
                : granularity.toLowerCase(Locale.ROOT);
        if (!g.equals("day") && !g.equals("week") && !g.equals("month")) {
            throw BusinessException.badRequest("INVALID_FILTER", "Granularity must be day, week or month.");
        }
        if (g.equals("day") && days > 92) {
            throw BusinessException.badRequest("INVALID_FILTER", "Daily figures are limited to 92 days; choose week or month.");
        }
        return new Filter(f, t, departmentId, g);
    }

    public long days() {
        return ChronoUnit.DAYS.between(from, to) + 1;
    }
}
