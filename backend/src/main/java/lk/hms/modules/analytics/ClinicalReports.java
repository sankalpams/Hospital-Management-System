package lk.hms.modules.analytics;

import lk.hms.common.settings.SettingsService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

import static lk.hms.modules.analytics.Sql.nz;
import static lk.hms.modules.analytics.Sql.pct;
import static lk.hms.modules.analytics.Sql.round;

/** Inpatient occupancy, laboratory turnaround and pharmacy consumption (report:operational). */
@Service
@Transactional(readOnly = true)
public class ClinicalReports {

    private final Sql sql;
    private final SettingsService settings;

    public ClinicalReports(Sql sql, SettingsService settings) {
        this.sql = sql;
        this.settings = settings;
    }

    // ------------------------------------------------------------------ inpatient

    public Report inpatient(Filter f) {
        MapSqlParameterSource p = sql.params(f);
        String wardJoin = " FROM beds b JOIN rooms r ON r.id = b.room_id JOIN wards w ON w.id = r.ward_id"
                + " WHERE w.active AND r.active AND (:dept IS NULL OR w.department_id = :dept)";
        // occupied minutes of each stay, clipped to the range and to now (a live stay counts up to now)
        String overlap = """
                SELECT w.id AS ward, SUM(GREATEST(0, TIMESTAMPDIFF(MINUTE, GREATEST(ba.assigned_at, :from),
                                                                   LEAST(COALESCE(ba.released_at, :now), :to, :now)))) / 60 AS hours
                FROM bed_assignments ba JOIN beds b ON b.id = ba.bed_id JOIN rooms r ON r.id = b.room_id JOIN wards w ON w.id = r.ward_id
                WHERE ba.assigned_at < :to AND COALESCE(ba.released_at, :now) > :from AND (:dept IS NULL OR w.department_id = :dept)
                GROUP BY w.id""";
        LocalDateTime from = (LocalDateTime) p.getValue("from");
        LocalDateTime end = ((LocalDateTime) p.getValue("to")).isBefore((LocalDateTime) p.getValue("now")) ? (LocalDateTime) p.getValue("to") : (LocalDateTime) p.getValue("now");
        BigDecimal rangeHours = BigDecimal.valueOf(Math.max(0, Duration.between(from, end).toMinutes())).divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);

        Map<Long, String> wardNames = new LinkedHashMap<>();
        Map<Long, BigDecimal> wardBeds = new HashMap<>();
        sql.jdbc.query("SELECT w.id, w.name, COUNT(b.id)" + wardJoin + " GROUP BY w.id, w.name ORDER BY w.name", p, rs -> {
            wardNames.put(rs.getLong(1), rs.getString(2));
            wardBeds.put(rs.getLong(1), rs.getBigDecimal(3));
        });
        Map<Long, BigDecimal> wardHours = new HashMap<>();
        sql.jdbc.query(overlap, p, rs -> {
            wardHours.put(rs.getLong(1), nz(rs.getBigDecimal(2)));
        });
        BigDecimal beds = wardBeds.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal occupied = wardHours.entrySet().stream().filter(e -> wardNames.containsKey(e.getKey())).map(Map.Entry::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);

        String adm = " FROM admissions a WHERE (:dept IS NULL OR a.department_id = :dept)";
        BigDecimal alos = sql.number("SELECT AVG(TIMESTAMPDIFF(MINUTE, a.admitted_at, a.discharged_at)) / 1440" + adm
                + " AND a.status = 'DISCHARGED' AND a.discharged_at >= :from AND a.discharged_at < :to", p);
        return new Report.Builder("inpatient", f)
                .kpi("occupancy", "Bed occupancy", pct(occupied, beds.multiply(rangeHours)), "percent", "/inpatients")
                .kpi("alos", "Average length of stay", round(alos, 1), "days")
                .kpi("admissions", "Admissions", sql.number("SELECT COUNT(*)" + adm + " AND a.admitted_at >= :from AND a.admitted_at < :to", p), "count")
                .kpi("discharges", "Discharges", sql.number("SELECT COUNT(*)" + adm + " AND a.discharged_at >= :from AND a.discharged_at < :to", p), "count")
                .kpi("inpatients", "In beds now", sql.number("SELECT COUNT(*)" + adm + " AND a.status = 'ADMITTED'", p), "count", "/inpatients?tab=admissions")
                .trend("admitted", "Admissions", "count", "flow",
                        sql.series("SELECT " + sql.bucket("a.admitted_at", f) + ", COUNT(*)" + adm + " AND a.admitted_at >= :from AND a.admitted_at < :to GROUP BY 1", p, f))
                .trend("discharged", "Discharges", "count", "flow",
                        sql.series("SELECT " + sql.bucket("a.discharged_at", f) + ", COUNT(*)" + adm + " AND a.discharged_at >= :from AND a.discharged_at < :to GROUP BY 1", p, f))
                .breakdown("occupancyByWard", "Occupancy by ward", "percent", wardNames.entrySet().stream()
                        .map(e -> new Report.Row(e.getValue(), pct(wardHours.getOrDefault(e.getKey(), BigDecimal.ZERO), wardBeds.get(e.getKey()).multiply(rangeHours))))
                        .toList())
                .breakdown("admissionType", "Admission type", "count", sql.rows("SELECT a.admission_type, COUNT(*)" + adm
                        + " AND a.admitted_at >= :from AND a.admitted_at < :to GROUP BY 1 ORDER BY 2 DESC", p))
                .breakdown("dischargeType", "Discharge type", "count", sql.rows("SELECT a.discharge_type, COUNT(*)" + adm
                        + " AND a.status = 'DISCHARGED' AND a.discharged_at >= :from AND a.discharged_at < :to GROUP BY 1 ORDER BY 2 DESC", p))
                .note("Occupancy = bed-hours occupied / (beds × hours in the range up to now), using today's bed count.")
                .build();
    }

    // ------------------------------------------------------------------ laboratory

    public Report laboratory(Filter f) {
        MapSqlParameterSource p = sql.params(f);
        String items = " FROM lab_request_items i JOIN lab_requests r ON r.id = i.lab_request_id JOIN lab_tests t ON t.id = i.lab_test_id"
                + " WHERE i.status <> 'CANCELLED' AND r.requested_at >= :from AND r.requested_at < :to AND (:dept IS NULL OR r.department_id = :dept)";
        String tat = "TIMESTAMPDIFF(MINUTE, r.requested_at, i.completed_at)";
        BigDecimal completed = sql.number("SELECT COUNT(*)" + items + " AND i.completed_at IS NOT NULL", p);
        BigDecimal onTime = sql.number("SELECT COUNT(*)" + items + " AND i.completed_at IS NOT NULL AND " + tat + " <= t.turnaround_hours * 60", p);
        BigDecimal numeric = sql.number("SELECT COUNT(*)" + items + " AND EXISTS (SELECT 1 FROM lab_results x WHERE x.lab_request_item_id = i.id AND x.flag IS NOT NULL)", p);
        BigDecimal abnormal = sql.number("SELECT COUNT(*)" + items + " AND EXISTS (SELECT 1 FROM lab_results x WHERE x.lab_request_item_id = i.id AND x.flag IN ('H','L'))", p);
        List<List<Object>> perTest = sql.jdbc.query("""
                SELECT t.name, COUNT(*), SUM(i.completed_at IS NOT NULL), AVG(CASE WHEN i.completed_at IS NOT NULL THEN %1$s END) / 60,
                       t.turnaround_hours, SUM(i.completed_at IS NOT NULL AND %1$s <= t.turnaround_hours * 60)
                %2$s GROUP BY t.id, t.name, t.turnaround_hours ORDER BY 2 DESC, 1""".formatted(tat, items), p,
                (rs, n) -> List.<Object>of(rs.getString(1), rs.getLong(2), rs.getLong(3), round(nz(rs.getBigDecimal(4)), 1), rs.getInt(5),
                        pct(BigDecimal.valueOf(rs.getLong(6)), BigDecimal.valueOf(rs.getLong(3)))));
        return new Report.Builder("laboratory", f)
                .kpi("tests", "Tests requested", sql.number("SELECT COUNT(*)" + items, p), "count", "/lab")
                .kpi("withinTarget", "Reported within target time", pct(onTime, completed), "percent")
                .kpi("avgTurnaround", "Average turnaround", round(sql.number("SELECT AVG(" + tat + ") / 60" + items + " AND i.completed_at IS NOT NULL", p), 1), "hours")
                .kpi("backlog", "Pending now", sql.number("""
                        SELECT COUNT(*) FROM lab_request_items i JOIN lab_requests r ON r.id = i.lab_request_id
                        WHERE i.status IN ('REQUESTED','SAMPLE_COLLECTED','PROCESSING') AND (:dept IS NULL OR r.department_id = :dept)""", p), "count", "/lab")
                .kpi("abnormal", "Abnormal numeric results", pct(abnormal, numeric), "percent")
                .trend("tests", "Tests requested", "count", "tests",
                        sql.series("SELECT " + sql.bucket("r.requested_at", f) + ", COUNT(*)" + items + " GROUP BY 1", p, f))
                .breakdown("byTest", "Tests by type", "count", perTest.stream().map(r -> new Report.Row((String) r.get(0), BigDecimal.valueOf((long) r.get(1)))).toList())
                .breakdown("priority", "Priority", "count", sql.rows("SELECT r.priority, COUNT(*)" + items
                        + " GROUP BY r.priority ORDER BY FIELD(r.priority, 'STAT', 'URGENT', 'ROUTINE')", p))
                .table("turnaround", "Turnaround by test", List.of("Test", "Requested", "Reported", "Avg hours", "Target hours", "Within target %"), perTest)
                .note("Turnaround runs from the request to the result being entered. Pending is the current backlog, not limited to the dates.")
                .build();
    }

    // ------------------------------------------------------------------ pharmacy

    public Report pharmacy(Filter f) {
        MapSqlParameterSource p = sql.params(f).addValue("alertDays", settings.getInt("pharmacy.expiry_alert_days", 90));
        String dispensed = """
                 FROM dispensation_items di JOIN dispensations d ON d.id = di.dispensation_id
                 JOIN prescriptions pr ON pr.id = d.prescription_id JOIN encounters e ON e.id = pr.encounter_id
                 JOIN medicine_batches b ON b.id = di.batch_id JOIN medicines m ON m.id = b.medicine_id
                 WHERE d.dispensed_at >= :from AND d.dispensed_at < :to AND (:dept IS NULL OR e.department_id = :dept)""";
        String usable = "(SELECT COALESCE(SUM(x.quantity_on_hand), 0) FROM medicine_batches x WHERE x.medicine_id = m.id AND x.expiry_date > :today)";
        List<List<Object>> low = sql.jdbc.query("SELECT CONCAT(m.generic_name, ' ', m.strength), " + usable + ", m.reorder_level, m.unit"
                        + " FROM medicines m WHERE m.active AND " + usable + " < m.reorder_level ORDER BY " + usable + " / GREATEST(m.reorder_level, 1), 1", p,
                (rs, n) -> List.<Object>of(rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getString(4)));
        List<List<Object>> expiring = sql.jdbc.query("""
                SELECT CONCAT(m.generic_name, ' ', m.strength), b.batch_no, b.expiry_date, b.quantity_on_hand,
                       CASE WHEN b.expiry_date <= :today THEN 'Expired' ELSE 'Expiring' END
                FROM medicine_batches b JOIN medicines m ON m.id = b.medicine_id
                WHERE b.quantity_on_hand > 0 AND b.expiry_date <= DATE_ADD(:today, INTERVAL :alertDays DAY)
                ORDER BY b.expiry_date""", p, (rs, n) -> List.<Object>of(rs.getString(1), rs.getString(2), rs.getDate(3).toString(), rs.getLong(4), rs.getString(5)));
        return new Report.Builder("pharmacy", f)
                .kpi("prescriptions", "Prescriptions dispensed", sql.number("SELECT COUNT(DISTINCT d.id)" + dispensed, p), "count", "/pharmacy")
                .kpi("units", "Units dispensed", sql.number("SELECT SUM(di.quantity)" + dispensed, p), "count")
                .kpi("value", "Value dispensed", sql.number("SELECT SUM(di.quantity * di.unit_price)" + dispensed, p), "LKR")
                .kpi("lowStock", "Below reorder level now", BigDecimal.valueOf(low.size()), "count", "/pharmacy?tab=alerts")
                .kpi("expiring", "Batches expiring or expired now", BigDecimal.valueOf(expiring.size()), "count", "/pharmacy?tab=alerts")
                .trend("units", "Units dispensed", "count", "units",
                        sql.series("SELECT " + sql.bucket("d.dispensed_at", f) + ", SUM(di.quantity)" + dispensed + " GROUP BY 1", p, f))
                .breakdown("topMedicines", "Top medicines by units", "count", sql.rows("SELECT CONCAT(m.generic_name, ' ', m.strength), SUM(di.quantity)"
                        + dispensed + " GROUP BY m.id, m.generic_name, m.strength ORDER BY 2 DESC LIMIT 10", p))
                .table("lowStock", "Below reorder level", List.of("Medicine", "Usable stock", "Reorder level", "Unit"), low)
                .table("expiring", "Expiring within " + p.getValue("alertDays") + " days", List.of("Medicine", "Batch", "Expiry", "On hand", "State"), expiring)
                .note("Stock tables show the position now, hospital-wide. Units dispensed follow the prescribing department.")
                .build();
    }
}
