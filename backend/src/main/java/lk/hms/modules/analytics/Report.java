package lk.hms.modules.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * One shape for every analytics report, so the UI and the CSV export can render any of them:
 * headline figures, time series (series sharing a {@code group} are drawn on one chart),
 * labelled breakdowns, and detail tables. {@code notes} explains what a filter does not apply to.
 */
public record Report(String report, LocalDate from, LocalDate to, String granularity, Long departmentId,
                     List<Kpi> kpis, List<Series> trends, List<Breakdown> breakdowns, List<Table> tables, List<String> notes) {

    /** unit: count, percent, LKR, minutes, hours, days */
    public record Kpi(String key, String label, BigDecimal value, String unit, String link) {
    }

    public record Point(String bucket, BigDecimal value) {
    }

    public record Series(String key, String label, String unit, String group, List<Point> points) {
    }

    public record Row(String label, BigDecimal value) {
    }

    public record Breakdown(String key, String label, String unit, List<Row> rows) {
    }

    public record Table(String key, String label, List<String> columns, List<List<Object>> rows) {
    }

    /** Mutable builder used by the report queries. */
    public static final class Builder {
        private final String report;
        private final Filter filter;
        private final List<Kpi> kpis = new ArrayList<>();
        private final List<Series> trends = new ArrayList<>();
        private final List<Breakdown> breakdowns = new ArrayList<>();
        private final List<Table> tables = new ArrayList<>();
        private final List<String> notes = new ArrayList<>();

        public Builder(String report, Filter filter) {
            this.report = report;
            this.filter = filter;
        }

        public Builder kpi(String key, String label, BigDecimal value, String unit) {
            return kpi(key, label, value, unit, null);
        }

        public Builder kpi(String key, String label, BigDecimal value, String unit, String link) {
            kpis.add(new Kpi(key, label, value, unit, link));
            return this;
        }

        public Builder trend(String key, String label, String unit, String group, List<Point> points) {
            trends.add(new Series(key, label, unit, group, points));
            return this;
        }

        public Builder breakdown(String key, String label, String unit, List<Row> rows) {
            breakdowns.add(new Breakdown(key, label, unit, rows));
            return this;
        }

        public Builder table(String key, String label, List<String> columns, List<List<Object>> rows) {
            tables.add(new Table(key, label, columns, rows));
            return this;
        }

        public Builder note(String note) {
            notes.add(note);
            return this;
        }

        public Report build() {
            return new Report(report, filter.from(), filter.to(), filter.granularity(), filter.departmentId(),
                    kpis, trends, breakdowns, tables, notes);
        }
    }
}
