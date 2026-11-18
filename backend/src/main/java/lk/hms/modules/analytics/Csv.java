package lk.hms.modules.analytics;

import java.math.BigDecimal;

/**
 * Long-format CSV of a report: one section per KPI block, series, breakdown and table.
 * Cells that a spreadsheet would treat as a formula are prefixed with an apostrophe.
 */
final class Csv {

    private Csv() {
    }

    static String of(Report r) {
        StringBuilder out = new StringBuilder("﻿");   // BOM so Excel opens UTF-8 correctly
        line(out, "Report", r.report(), "From", r.from(), "To", r.to(), "Granularity", r.granularity(),
                "Department", r.departmentId() == null ? "All" : r.departmentId());
        out.append("\r\n");
        line(out, "Figure", "Value", "Unit");
        r.kpis().forEach(k -> line(out, k.label(), k.value(), k.unit()));
        for (Report.Series s : r.trends()) {
            out.append("\r\n");
            line(out, s.label(), "Period starting", "Value");
            s.points().forEach(pt -> line(out, "", pt.bucket(), pt.value()));
        }
        for (Report.Breakdown b : r.breakdowns()) {
            out.append("\r\n");
            line(out, b.label(), "Value", "Unit");
            b.rows().forEach(row -> line(out, row.label(), row.value(), b.unit()));
        }
        for (Report.Table t : r.tables()) {
            out.append("\r\n");
            line(out, t.label());
            line(out, t.columns().toArray());
            t.rows().forEach(row -> line(out, row.toArray()));
        }
        if (!r.notes().isEmpty()) {
            out.append("\r\n");
            r.notes().forEach(n -> line(out, n));
        }
        return out.toString();
    }

    private static void line(StringBuilder out, Object... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(cell(cells[i]));
        }
        out.append("\r\n");
    }

    static String cell(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof BigDecimal d) {
            return d.toPlainString();
        }
        String s = v.toString();
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0 && !(v instanceof Number)) {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
