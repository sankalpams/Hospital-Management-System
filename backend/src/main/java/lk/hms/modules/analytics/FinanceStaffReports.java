package lk.hms.modules.analytics;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static lk.hms.modules.analytics.OperationalReports.CHARGE_DEPT;
import static lk.hms.modules.analytics.OperationalReports.WEEKDAYS;
import static lk.hms.modules.analytics.OperationalReports.fill;
import static lk.hms.modules.analytics.Sql.pct;

/** Revenue (report:financial) and staff (report:staff). */
@Service
@Transactional(readOnly = true)
public class FinanceStaffReports {

    private final Sql sql;

    public FinanceStaffReports(Sql sql) {
        this.sql = sql;
    }

    // ------------------------------------------------------------------ revenue

    public Report revenue(Filter f) {
        MapSqlParameterSource p = sql.params(f);
        String issued = " i.status <> 'VOID' AND i.issued_at >= :from AND i.issued_at < :to";
        // with a department filter, billed = that department's invoice lines (discounts are invoice-wide and not split)
        String lines = " FROM invoice_items ii JOIN invoices i ON i.id = ii.invoice_id JOIN charge_items c ON c.id = ii.charge_item_id WHERE"
                + issued + " AND (:dept IS NULL OR " + CHARGE_DEPT + " = :dept)";
        boolean byDept = f.departmentId() != null;
        String billedSql = byDept ? "SELECT SUM(ii.line_total)" + lines : "SELECT SUM(i.total_amount) FROM invoices i WHERE" + issued;
        String billedTrend = byDept
                ? "SELECT " + sql.bucket("i.issued_at", f) + ", SUM(ii.line_total)" + lines + " GROUP BY 1"
                : "SELECT " + sql.bucket("i.issued_at", f) + ", SUM(i.total_amount) FROM invoices i WHERE" + issued + " GROUP BY 1";
        String paid = " FROM payments pm WHERE pm.received_at >= :from AND pm.received_at < :to";
        BigDecimal billed = sql.number(billedSql, p);
        BigDecimal collected = sql.number("SELECT SUM(pm.amount)" + paid, p);
        String age = "DATEDIFF(:today, DATE(" + sql.local("i.issued_at") + "))";
        Report.Builder b = new Report.Builder("revenue", f)
                .kpi("billed", "Billed", billed, "LKR", "/billing")
                .kpi("collected", "Collected (net of refunds)", collected, "LKR")
                .kpi("collectionRate", "Collected / billed", pct(collected, billed), "percent")
                .kpi("discounts", "Discounts given", sql.number("SELECT SUM(i.discount_amount) FROM invoices i WHERE" + issued, p), "LKR")
                .kpi("outstanding", "Outstanding now", sql.number("SELECT SUM(i.balance_due) FROM invoices i WHERE i.status IN ('ISSUED','PARTIALLY_PAID')", p), "LKR", "/billing?status=ISSUED")
                .kpi("unbilled", "Charges not yet invoiced", sql.number("SELECT SUM(c.amount) FROM charge_items c WHERE c.invoice_id IS NULL", p), "LKR")
                .trend("billed", "Billed", "LKR", "money", sql.series(billedTrend, p, f))
                .trend("collected", "Collected", "LKR", "money",
                        sql.series("SELECT " + sql.bucket("pm.received_at", f) + ", SUM(pm.amount)" + paid + " GROUP BY 1", p, f))
                .breakdown("byCategory", "Billed by category", "LKR", sql.rows("SELECT ii.category, SUM(ii.line_total)" + lines + " GROUP BY ii.category ORDER BY 2 DESC", p))
                .breakdown("byMethod", "Collections by method", "LKR", sql.rows("SELECT pm.method, SUM(pm.amount)" + paid + " GROUP BY pm.method ORDER BY 2 DESC", p))
                .breakdown("ageing", "Outstanding by age", "LKR", fill(List.of("0-30 days", "31-60 days", "61-90 days", "Over 90 days"), sql.rows("""
                        SELECT CASE WHEN %1$s <= 30 THEN '0-30 days' WHEN %1$s <= 60 THEN '31-60 days' WHEN %1$s <= 90 THEN '61-90 days' ELSE 'Over 90 days' END,
                               SUM(i.balance_due)
                        FROM invoices i WHERE i.status IN ('ISSUED','PARTIALLY_PAID') GROUP BY 1""".formatted(age), p)));
        b.note("Billed = issued, non-void invoices by issue date. Collected = payments by receipt date, refunds subtracted.");
        if (byDept) {
            b.note("With a department selected, billed figures are that department's invoice lines before invoice-wide discounts; collections, outstanding and unbilled are hospital-wide.");
        }
        return b.build();
    }

    // ------------------------------------------------------------------ staff

    public Report staff(Filter f) {
        MapSqlParameterSource p = sql.params(f);
        String emp = " FROM employees e WHERE (:dept IS NULL OR e.department_id = :dept)";
        String att = " FROM attendance a JOIN employees e ON e.id = a.employee_id WHERE a.work_date BETWEEN :fromDate AND :toDate AND (:dept IS NULL OR e.department_id = :dept)";
        String rate = "100 * (SUM(a.status = 'PRESENT') + 0.5 * SUM(a.status = 'HALF_DAY')) / NULLIF(SUM(a.status <> 'ON_LEAVE'), 0)";
        String leave = " FROM leave_requests l JOIN employees e ON e.id = l.employee_id JOIN leave_types t ON t.id = l.leave_type_id"
                + " WHERE l.status = 'APPROVED' AND l.start_date BETWEEN :fromDate AND :toDate AND (:dept IS NULL OR e.department_id = :dept)";
        return new Report.Builder("staff", f)
                .kpi("headcount", "Headcount now", sql.number("SELECT COUNT(*)" + emp + " AND e.status IN ('ACTIVE','ON_LEAVE')", p), "count", "/staff")
                .kpi("joiners", "Joined", sql.number("SELECT COUNT(*)" + emp + " AND e.join_date BETWEEN :fromDate AND :toDate", p), "count")
                .kpi("leavers", "Left", sql.number("SELECT COUNT(*)" + emp + " AND e.leave_date BETWEEN :fromDate AND :toDate", p), "count")
                .kpi("attendanceRate", "Attendance rate", Sql.round(sql.number("SELECT " + rate + att, p), 1), "percent", "/staff?tab=attendance")
                .kpi("leaveDays", "Approved leave days", sql.number("SELECT SUM(l.days)" + leave, p), "days", "/staff?tab=leave")
                .kpi("onLeaveToday", "On leave today", sql.number("""
                        SELECT COUNT(DISTINCT l.employee_id) FROM leave_requests l JOIN employees e ON e.id = l.employee_id
                        WHERE l.status = 'APPROVED' AND :today BETWEEN l.start_date AND l.end_date AND (:dept IS NULL OR e.department_id = :dept)""", p), "count")
                .trend("attendance", "Attendance rate", "percent", "attendance",
                        sql.series("SELECT " + sql.dateBucket("a.work_date", f) + ", ROUND(" + rate + ", 1)" + att + " GROUP BY 1", p, f))
                .breakdown("byCategory", "Headcount by category", "count", sql.rows("SELECT e.staff_category, COUNT(*)" + emp
                        + " AND e.status IN ('ACTIVE','ON_LEAVE') GROUP BY 1 ORDER BY 2 DESC", p))
                .breakdown("byDepartment", "Headcount by department", "count", sql.rows("""
                        SELECT d.name, COUNT(*) FROM employees e JOIN departments d ON d.id = e.department_id
                        WHERE e.status IN ('ACTIVE','ON_LEAVE') AND (:dept IS NULL OR e.department_id = :dept) GROUP BY d.name ORDER BY 2 DESC""", p))
                .breakdown("leaveByType", "Approved leave days by type", "days", sql.rows("SELECT t.name, SUM(l.days)" + leave + " GROUP BY t.name ORDER BY 2 DESC", p))
                .breakdown("absencesByWeekday", "Absences by weekday", "count", fill(WEEKDAYS, sql.rows(
                        "SELECT ELT(WEEKDAY(a.work_date) + 1, 'Mon','Tue','Wed','Thu','Fri','Sat','Sun'), COUNT(*)" + att + " AND a.status = 'ABSENT' GROUP BY 1", p)))
                .note("Attendance rate = (present + half of half-days) / days recorded, excluding approved leave.")
                .build();
    }
}
