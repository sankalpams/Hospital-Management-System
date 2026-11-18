package lk.hms.modules.analytics;

import lk.hms.common.security.CurrentUser;
import lk.hms.common.security.HmsUserPrincipal;
import lk.hms.common.settings.SettingsService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;

import static lk.hms.modules.analytics.Sql.pct;

/**
 * The home dashboard: today's figures for the modules the caller can use, and a two-week trend.
 * Each card appears only with the permission that guards the screen it links to.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final Sql sql;
    private final SettingsService settings;

    public DashboardService(Sql sql, SettingsService settings) {
        this.sql = sql;
        this.settings = settings;
    }

    public Report forCurrentUser() {
        HmsUserPrincipal me = CurrentUser.require();
        Set<String> can = me.getPermissions();
        Filter today = Filter.of(sql.today(), sql.today(), null, "day", sql.today());
        Filter fortnight = Filter.of(sql.today().minusDays(13), sql.today(), null, "day", sql.today());
        MapSqlParameterSource p = sql.params(today).addValue("me", me.getId())
                .addValue("alertDays", settings.getInt("pharmacy.expiry_alert_days", 90));
        Report.Builder b = new Report.Builder("dashboard", fortnight);

        String todays = " FROM appointments a WHERE a.slot_start >= :from AND a.slot_start < :to";
        Long myDoctor = sql.jdbc.query("SELECT d.id FROM doctors d JOIN employees e ON e.id = d.employee_id WHERE e.user_id = :me", p,
                rs -> rs.next() ? rs.getLong(1) : null);
        if (myDoctor != null && can.contains("appointment:consult")) {
            p.addValue("doc", myDoctor);
            b.kpi("myToday", "My appointments today", sql.number("SELECT COUNT(*)" + todays + " AND a.doctor_id = :doc AND a.status NOT IN ('CANCELLED')", p), "count", "/appointments?mine=true")
             .kpi("myWaiting", "My patients waiting", sql.number("SELECT COUNT(*)" + todays + " AND a.doctor_id = :doc AND a.status = 'CHECKED_IN'", p), "count", "/appointments?mine=true");
        }
        if (can.contains("appointment:read")) {
            b.kpi("appointmentsToday", "Appointments today", sql.number("SELECT COUNT(*)" + todays + " AND a.status <> 'CANCELLED'", p), "count", "/appointments")
             .kpi("waiting", "Checked in, waiting", sql.number("SELECT COUNT(*)" + todays + " AND a.status = 'CHECKED_IN'", p), "count", "/appointments")
             .kpi("completedToday", "Completed today", sql.number("SELECT COUNT(*)" + todays + " AND a.status = 'COMPLETED'", p), "count", "/appointments");
            MapSqlParameterSource fp = sql.params(fortnight);
            b.trend("appointments", "Appointments", "count", "appointments", sql.series("SELECT " + sql.bucket("a.slot_start", fortnight)
                    + ", COUNT(*) FROM appointments a WHERE a.slot_start >= :from AND a.slot_start < :to AND a.status <> 'CANCELLED' GROUP BY 1", fp, fortnight));
        }
        if (can.contains("patient:read")) {
            b.kpi("newPatients", "New patients today", sql.number("SELECT COUNT(*) FROM patients WHERE created_at >= :from AND created_at < :to", p), "count", "/patients");
        }
        if (can.contains("admission:read")) {
            BigDecimal beds = sql.number("SELECT COUNT(*) FROM beds b JOIN rooms r ON r.id = b.room_id JOIN wards w ON w.id = r.ward_id WHERE w.active AND r.active", p);
            BigDecimal occupied = sql.number("SELECT COUNT(*) FROM beds b JOIN rooms r ON r.id = b.room_id JOIN wards w ON w.id = r.ward_id WHERE w.active AND r.active AND b.status = 'OCCUPIED'", p);
            b.kpi("occupancy", "Beds occupied", pct(occupied, beds), "percent", "/inpatients")
             .kpi("awaitingBed", "Waiting for a bed", sql.number("SELECT COUNT(*) FROM admissions WHERE status = 'REQUESTED'", p), "count", "/inpatients?tab=admissions");
        }
        if (can.contains("lab:request:read")) {
            String pending = " FROM lab_request_items i JOIN lab_requests r ON r.id = i.lab_request_id WHERE i.status IN ('REQUESTED','SAMPLE_COLLECTED','PROCESSING')";
            b.kpi("labPending", "Lab tests pending", sql.number("SELECT COUNT(*)" + pending, p), "count", "/lab")
             .kpi("labUrgent", "Urgent or STAT pending", sql.number("SELECT COUNT(*)" + pending + " AND r.priority IN ('URGENT','STAT')", p), "count", "/lab");
        }
        if (can.contains("prescription:read")) {
            b.kpi("pharmacyQueue", "Prescriptions to dispense", sql.number("SELECT COUNT(*) FROM prescriptions WHERE status IN ('ACTIVE','PARTIALLY_DISPENSED')", p), "count", "/pharmacy");
        }
        if (can.contains("medicine:read")) {
            b.kpi("lowStock", "Medicines below reorder level", sql.number("""
                    SELECT COUNT(*) FROM medicines m WHERE m.active AND m.reorder_level >
                      (SELECT COALESCE(SUM(x.quantity_on_hand), 0) FROM medicine_batches x WHERE x.medicine_id = m.id AND x.expiry_date > :today)""", p), "count", "/pharmacy?tab=alerts")
             .kpi("expiring", "Batches expiring soon", sql.number("""
                    SELECT COUNT(*) FROM medicine_batches WHERE quantity_on_hand > 0 AND expiry_date <= DATE_ADD(:today, INTERVAL :alertDays DAY)""", p), "count", "/pharmacy?tab=alerts");
        }
        if (can.contains("billing:read")) {
            b.kpi("collectedToday", "Collected today", sql.number("SELECT SUM(amount) FROM payments WHERE received_at >= :from AND received_at < :to", p), "LKR", "/billing")
             .kpi("outstanding", "Outstanding invoices", sql.number("SELECT SUM(balance_due) FROM invoices WHERE status IN ('ISSUED','PARTIALLY_PAID')", p), "LKR", "/billing");
        }
        if (can.contains("report:financial")) {
            MapSqlParameterSource fp = sql.params(fortnight);
            b.trend("collected", "Collected", "LKR", "money", sql.series("SELECT " + sql.bucket("received_at", fortnight)
                    + ", SUM(amount) FROM payments WHERE received_at >= :from AND received_at < :to GROUP BY 1", fp, fortnight));
        }
        if (can.contains("leave:approve")) {
            b.kpi("leavePending", "Leave requests to decide", sql.number("SELECT COUNT(*) FROM leave_requests WHERE status = 'PENDING'", p), "count", "/staff?tab=leave");
        }
        if (can.contains("employee:read")) {
            b.kpi("onLeave", "Staff on leave today", sql.number(
                    "SELECT COUNT(DISTINCT employee_id) FROM leave_requests WHERE status = 'APPROVED' AND :today BETWEEN start_date AND end_date", p), "count", "/staff");
        }
        return b.build();
    }
}
