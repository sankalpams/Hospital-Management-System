package lk.hms.modules.analytics;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

import static lk.hms.modules.analytics.Sql.pct;
import static lk.hms.modules.analytics.Sql.round;

/** Patients, appointments, doctor workload and departments (report:operational). */
@Service
@Transactional(readOnly = true)
public class OperationalReports {

    /** Department a charge belongs to; pharmacy lines inherit the prescribing encounter's department. */
    static final String CHARGE_DEPT = """
            COALESCE(c.department_id, (SELECT en.department_id FROM dispensation_items di
                     JOIN prescription_items pi ON pi.id = di.prescription_item_id
                     JOIN prescriptions pr ON pr.id = pi.prescription_id
                     JOIN encounters en ON en.id = pr.encounter_id
                     WHERE c.source_type = 'DISPENSATION_ITEM' AND di.id = c.source_id))""";

    static final List<String> WEEKDAYS = List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun");

    private final Sql sql;

    public OperationalReports(Sql sql) {
        this.sql = sql;
    }

    // ------------------------------------------------------------------ patients

    public Report patients(Filter f) {
        MapSqlParameterSource p = sql.params(f);
        String registered = "p.created_at >= :from AND p.created_at < :to";
        String seen = "SELECT e.patient_id FROM encounters e WHERE e.started_at >= :from AND e.started_at < :to AND (:dept IS NULL OR e.department_id = :dept)";
        String age = "TIMESTAMPDIFF(YEAR, p.date_of_birth, :toDate)";
        return new Report.Builder("patients", f)
                .kpi("registered", "New registrations", sql.number("SELECT COUNT(*) FROM patients p WHERE " + registered, p), "count", "/patients")
                .kpi("seen", "Patients seen", sql.number("SELECT COUNT(DISTINCT x.patient_id) FROM (" + seen + ") x", p), "count")
                .kpi("active", "Active patients on record", sql.number("SELECT COUNT(*) FROM patients WHERE status = 'ACTIVE'", p), "count")
                .trend("registrations", "Registrations", "count", "registrations", sql.series(
                        "SELECT " + sql.bucket("p.created_at", f) + ", COUNT(*) FROM patients p WHERE " + registered + " GROUP BY 1", p, f))
                .breakdown("gender", "Patients seen by gender", "count", sql.rows(
                        "SELECT CONCAT(UPPER(LEFT(p.gender, 1)), LOWER(SUBSTRING(p.gender, 2))), COUNT(*) FROM patients p WHERE p.id IN (" + seen + ") GROUP BY p.gender ORDER BY 2 DESC", p))
                .breakdown("age", "Patients seen by age", "count", sql.rows("""
                        SELECT CASE WHEN %1$s < 14 THEN '0-13' WHEN %1$s < 25 THEN '14-24' WHEN %1$s < 45 THEN '25-44'
                                    WHEN %1$s < 65 THEN '45-64' ELSE '65+' END AS band, COUNT(*)
                        FROM patients p WHERE p.id IN (%2$s) GROUP BY band ORDER BY MIN(%1$s)""".formatted(age, seen), p))
                .breakdown("district", "Patients seen by district (top 10)", "count", sql.rows(
                        "SELECT COALESCE(p.district, 'Not recorded'), COUNT(*) FROM patients p WHERE p.id IN (" + seen + ") GROUP BY 1 ORDER BY 2 DESC, 1 LIMIT 10", p))
                .note("Registrations are hospital-wide; the department filter applies to patients seen.")
                .build();
    }

    // ------------------------------------------------------------------ appointments

    public Report appointments(Filter f) {
        MapSqlParameterSource p = sql.params(f);
        String where = " FROM appointments a WHERE a.slot_start >= :from AND a.slot_start < :to AND (:dept IS NULL OR a.department_id = :dept)";
        BigDecimal total = sql.number("SELECT COUNT(*)" + where, p);
        BigDecimal completed = sql.number("SELECT COUNT(*)" + where + " AND a.status = 'COMPLETED'", p);
        BigDecimal cancelled = sql.number("SELECT COUNT(*)" + where + " AND a.status = 'CANCELLED'", p);
        BigDecimal noShow = sql.number("SELECT COUNT(*)" + where + " AND a.status = 'NO_SHOW'", p);
        BigDecimal wait = sql.number("SELECT AVG(GREATEST(0, TIMESTAMPDIFF(SECOND, a.checked_in_at, a.started_at)) / 60)" + where
                + " AND a.checked_in_at IS NOT NULL AND a.started_at IS NOT NULL", p);
        String slot = sql.local("a.slot_start");
        return new Report.Builder("appointments", f)
                .kpi("total", "Appointments", total, "count", "/appointments")
                .kpi("completed", "Completed", completed, "count")
                .kpi("cancellationRate", "Cancellation rate", pct(cancelled, total), "percent")
                .kpi("noShowRate", "No-show rate", pct(noShow, completed.add(noShow)), "percent")
                .kpi("avgWait", "Average wait (check-in to start)", round(wait, 1), "minutes")
                .trend("booked", "Appointments", "count", "volume",
                        sql.series("SELECT " + sql.bucket("a.slot_start", f) + ", COUNT(*)" + where + " GROUP BY 1", p, f))
                .trend("completed", "Completed", "count", "volume",
                        sql.series("SELECT " + sql.bucket("a.slot_start", f) + ", COUNT(*)" + where + " AND a.status = 'COMPLETED' GROUP BY 1", p, f))
                .breakdown("status", "Status mix", "count", sql.rows("SELECT a.status, COUNT(*)" + where
                        + " GROUP BY a.status ORDER BY FIELD(a.status, 'PENDING','CONFIRMED','CHECKED_IN','IN_PROGRESS','COMPLETED','NO_SHOW','CANCELLED')", p))
                .breakdown("type", "Appointment type", "count", sql.rows("SELECT a.type, COUNT(*)" + where + " GROUP BY a.type ORDER BY 2 DESC", p))
                .breakdown("cancelByWeekday", "Cancellations by weekday", "count", fill(WEEKDAYS, sql.rows(
                        "SELECT ELT(WEEKDAY(" + slot + ") + 1, 'Mon','Tue','Wed','Thu','Fri','Sat','Sun'), COUNT(*)" + where
                                + " AND a.status = 'CANCELLED' GROUP BY 1", p)))
                .breakdown("cancelByHour", "Cancellations by slot hour", "count", sql.rows(
                        "SELECT CONCAT(LPAD(HOUR(" + slot + "), 2, '0'), ':00') h, COUNT(*)" + where + " AND a.status = 'CANCELLED' GROUP BY h ORDER BY h", p))
                .breakdown("cancelByDepartment", "Cancellations by department", "count", sql.rows(
                        "SELECT d.name, COUNT(*)" + where.replace("FROM appointments a", "FROM appointments a JOIN departments d ON d.id = a.department_id")
                                + " AND a.status = 'CANCELLED' GROUP BY d.name ORDER BY 2 DESC", p))
                .note("No-show rate = no-shows / (completed + no-shows). Wait is measured from check-in to the doctor starting.")
                .build();
    }

    // ------------------------------------------------------------------ doctors

    public Report doctorWorkload(Filter f) {
        MapSqlParameterSource p = sql.params(f);
        List<List<Object>> rows = sql.jdbc.query("""
                SELECT CONCAT('Dr. ', e.first_name, ' ', e.last_name) AS name, dp.name AS dept,
                       SUM(a.status = 'COMPLETED') AS completed,
                       COUNT(DISTINCT CASE WHEN a.status = 'COMPLETED' THEN a.patient_id END) AS patients,
                       AVG(CASE WHEN a.status = 'COMPLETED' AND a.started_at IS NOT NULL AND a.completed_at IS NOT NULL
                                THEN TIMESTAMPDIFF(SECOND, a.started_at, a.completed_at) / 60 END) AS avg_min,
                       SUM(a.status = 'NO_SHOW') AS no_shows
                FROM appointments a
                JOIN doctors d ON d.id = a.doctor_id
                JOIN employees e ON e.id = d.employee_id
                JOIN departments dp ON dp.id = a.department_id
                WHERE a.slot_start >= :from AND a.slot_start < :to AND (:dept IS NULL OR a.department_id = :dept)
                GROUP BY d.id, name, dept
                ORDER BY completed DESC, name""", p, (rs, i) -> List.<Object>of(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getLong(4),
                round(Sql.nz(rs.getBigDecimal(5)), 1), rs.getLong(6)));
        BigDecimal avg = sql.number("""
                SELECT AVG(TIMESTAMPDIFF(SECOND, a.started_at, a.completed_at) / 60) FROM appointments a
                WHERE a.status = 'COMPLETED' AND a.slot_start >= :from AND a.slot_start < :to AND (:dept IS NULL OR a.department_id = :dept)
                  AND a.started_at IS NOT NULL AND a.completed_at IS NOT NULL""", p);
        long consultations = rows.stream().mapToLong(r -> (long) r.get(2)).sum();
        return new Report.Builder("doctors", f)
                .kpi("consultations", "Consultations", BigDecimal.valueOf(consultations), "count")
                .kpi("doctors", "Doctors consulting", BigDecimal.valueOf(rows.stream().filter(r -> (long) r.get(2) > 0).count()), "count", "/doctors")
                .kpi("avgConsult", "Average consultation", round(avg, 1), "minutes")
                .breakdown("consultationsByDoctor", "Consultations per doctor", "count",
                        rows.stream().limit(15).map(r -> new Report.Row((String) r.get(0), BigDecimal.valueOf((long) r.get(2)))).toList())
                .table("workload", "Doctor workload", List.of("Doctor", "Department", "Consultations", "Patients", "Avg minutes", "No-shows"), rows)
                .build();
    }

    // ------------------------------------------------------------------ departments

    public Report departments(Filter f) {
        MapSqlParameterSource p = sql.params(f);
        Map<Long, Object[]> byDept = new LinkedHashMap<>();   // name, patients, appointments, completed, admissions, revenue
        sql.jdbc.query("SELECT id, name FROM departments WHERE type = 'CLINICAL' AND (:dept IS NULL OR id = :dept) ORDER BY name", p, rs -> {
            byDept.put(rs.getLong(1), new Object[]{rs.getString(2), 0L, 0L, 0L, 0L, BigDecimal.ZERO});
        });
        merge(byDept, 1, "SELECT e.department_id, COUNT(DISTINCT e.patient_id) FROM encounters e WHERE e.started_at >= :from AND e.started_at < :to GROUP BY 1", p);
        merge(byDept, 2, "SELECT a.department_id, COUNT(*) FROM appointments a WHERE a.slot_start >= :from AND a.slot_start < :to GROUP BY 1", p);
        merge(byDept, 3, "SELECT a.department_id, COUNT(*) FROM appointments a WHERE a.status = 'COMPLETED' AND a.slot_start >= :from AND a.slot_start < :to GROUP BY 1", p);
        merge(byDept, 4, "SELECT ad.department_id, COUNT(*) FROM admissions ad WHERE ad.admitted_at >= :from AND ad.admitted_at < :to GROUP BY 1", p);
        sql.jdbc.query("SELECT " + CHARGE_DEPT + """
                 AS dept, SUM(c.amount) FROM charge_items c JOIN invoices i ON i.id = c.invoice_id
                WHERE i.status <> 'VOID' AND c.service_date >= :from AND c.service_date < :to GROUP BY dept""", p, rs -> {
            Object[] row = byDept.get(rs.getLong(1));
            if (row != null) {
                row[5] = rs.getBigDecimal(2);
            }
        });
        List<List<Object>> rows = byDept.values().stream().map(r -> List.of(r)).toList();
        return new Report.Builder("departments", f)
                .kpi("patients", "Patients seen", BigDecimal.valueOf(byDept.values().stream().mapToLong(r -> (long) r[1]).sum()), "count")
                .kpi("revenue", "Invoiced revenue", byDept.values().stream().map(r -> (BigDecimal) r[5]).reduce(BigDecimal.ZERO, BigDecimal::add), "LKR")
                .breakdown("patientsByDepartment", "Patients seen", "count",
                        byDept.values().stream().map(r -> new Report.Row((String) r[0], BigDecimal.valueOf((long) r[1]))).toList())
                .breakdown("revenueByDepartment", "Invoiced revenue", "LKR",
                        byDept.values().stream().map(r -> new Report.Row((String) r[0], (BigDecimal) r[5])).toList())
                .table("departments", "Departments", List.of("Department", "Patients", "Appointments", "Completed", "Admissions", "Revenue (LKR)"), rows)
                .note("A patient seen by two departments counts once in each. Revenue = invoiced charges by service date, before invoice discounts; medicines count towards the prescribing department.")
                .build();
    }

    private void merge(Map<Long, Object[]> byDept, int index, String query, MapSqlParameterSource p) {
        sql.jdbc.query(query, p, rs -> {
            Object[] row = byDept.get(rs.getLong(1));
            if (row != null) {
                row[index] = rs.getLong(2);
            }
        });
    }

    /** Puts rows in the given label order, adding zero rows for missing labels. */
    static List<Report.Row> fill(List<String> labels, List<Report.Row> rows) {
        Map<String, BigDecimal> m = new HashMap<>();
        rows.forEach(r -> m.put(r.label(), r.value()));
        return labels.stream().map(l -> new Report.Row(l, m.getOrDefault(l, BigDecimal.ZERO))).toList();
    }
}
