package lk.hms.modules.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import lk.hms.support.Api;
import lk.hms.support.Fixtures;
import lk.hms.support.IntegrationTest;
import lk.hms.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Clock: Mon 2030-06-03 08:30 Colombo. */
@IntegrationTest
class FinanceStaffDashboardIntegrationTest {

    @Autowired Api api;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;

    Api.Session admin, desk, doctor;
    long dept, doctorId;

    @BeforeEach
    void setUp() throws Exception {
        clock.reset();
        admin = api.as("HOSPITAL_ADMIN");
        desk = api.as("RECEPTIONIST");
        doctor = api.as("DOCTOR");
        dept = Fixtures.department(admin);
        doctorId = Fixtures.doctor(admin, dept, doctor.user.getId());
        Fixtures.schedule(admin, doctorId, 1, "09:00", "12:00", 15, 4);
    }

    private static BigDecimal kpi(JsonNode report, String key) {
        for (JsonNode k : report.get("kpis")) if (k.get("key").asText().equals(key)) return k.get("value").decimalValue();
        throw new AssertionError("no kpi " + key + " in " + report.get("kpis"));
    }

    private static boolean hasKpi(JsonNode report, String key) {
        for (JsonNode k : report.get("kpis")) if (k.get("key").asText().equals(key)) return true;
        return false;
    }

    private static BigDecimal sum(JsonNode rows) {
        BigDecimal s = BigDecimal.ZERO;
        for (JsonNode r : rows) s = s.add(r.get("value").decimalValue());
        return s;
    }

    @Test
    void revenueByDepartmentAndHospitalWideAgainstSql() throws Exception {
        long patient = Fixtures.patient(desk);
        long apt = Fixtures.book(desk, patient, doctorId, "2030-06-03", "09:00").body().get("id").asLong();
        desk.post("/api/v1/appointments/" + apt + "/check-in", null);
        doctor.post("/api/v1/appointments/" + apt + "/start", null);
        long enc = doctor.get("/api/v1/patients/" + patient + "/emr").body().at("/encounters/0/id").asLong();
        doctor.post("/api/v1/encounters/" + enc + "/diagnoses", Map.of("icd10Code", "I10", "description", "Hypertension", "type", "PRIMARY", "certainty", "CONFIRMED"));
        doctor.post("/api/v1/appointments/" + apt + "/complete", null);
        List<Long> charges = new ArrayList<>();
        desk.get("/api/v1/patients/" + patient + "/unbilled-charges").body().forEach(c -> charges.add(c.get("id").asLong()));
        long inv = desk.post("/api/v1/invoices", Map.of("patientId", patient, "chargeIds", charges, "discountAmount", "500.00",
                "discountReason", "Staff family")).body().get("id").asLong();
        desk.post("/api/v1/invoices/" + inv + "/issue", null);
        desk.post("/api/v1/invoices/" + inv + "/payments", Map.of("amount", "1000.00", "method", "CARD", "referenceNo", "APPR 1"));

        JsonNode d = admin.get("/api/v1/analytics/revenue?from=2030-06-01&to=2030-06-30&departmentId=" + dept).body();
        assertThat(kpi(d, "billed")).as("line totals before the invoice discount").isEqualByComparingTo("2500.00");
        assertThat(d.at("/breakdowns/0/rows/0/label").asText()).isEqualTo("CONSULTATION");
        assertThat(d.get("notes").toString()).contains("hospital-wide");

        JsonNode all = admin.get("/api/v1/analytics/revenue?from=2030-06-01&to=2030-06-30").body();
        BigDecimal billed = jdbc.queryForObject("SELECT COALESCE(SUM(total_amount), 0) FROM invoices WHERE status <> 'VOID' AND issued_at >= '2030-05-31 18:30:00' AND issued_at < '2030-06-30 18:30:00'", BigDecimal.class);
        BigDecimal collected = jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE received_at >= '2030-05-31 18:30:00' AND received_at < '2030-06-30 18:30:00'", BigDecimal.class);
        BigDecimal outstanding = jdbc.queryForObject("SELECT COALESCE(SUM(total_amount - amount_paid), 0) FROM invoices WHERE status IN ('ISSUED','PARTIALLY_PAID')", BigDecimal.class);
        assertThat(kpi(all, "billed")).isEqualByComparingTo(billed);
        assertThat(kpi(all, "collected")).isEqualByComparingTo(collected);
        assertThat(kpi(all, "outstanding")).isEqualByComparingTo(outstanding);
        assertThat(sum(all.at("/breakdowns/2/rows"))).as("ageing buckets add up to outstanding").isEqualByComparingTo(outstanding);
        assertThat(sum(all.at("/trends/1/points"))).isEqualByComparingTo(collected);
        assertThat(sum(all.at("/breakdowns/1/rows"))).isEqualByComparingTo(collected);

        assertThat(api.as("ACCOUNTANT").get("/api/v1/analytics/revenue").status()).isEqualTo(200);
        assertThat(api.as("HR_MANAGER").get("/api/v1/analytics/revenue").status()).isEqualTo(403);
    }

    @Test
    void staffAttendanceAndLeave() throws Exception {
        Api.Session hr = api.as("HR_MANAGER");
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Map<String, Object> b = new HashMap<>(Map.of("firstName", "Report", "lastName", "Staff " + Fixtures.uid(), "gender", "FEMALE",
                    "phone", "0771234567", "departmentId", dept, "designation", "Nursing Officer", "staffCategory", "NURSE",
                    "employmentType", "PERMANENT", "joinDate", "2024-01-15"));
            ids.add(hr.post("/api/v1/employees", b).body().get("id").asLong());
        }
        hr.put("/api/v1/attendance?date=2030-06-03", Map.of("entries", List.of(
                Map.of("employeeId", ids.get(0), "status", "PRESENT", "clockIn", "08:00"),
                Map.of("employeeId", ids.get(1), "status", "HALF_DAY", "clockIn", "08:00"))));
        long leave = hr.post("/api/v1/leave-requests", Map.of("employeeId", ids.get(1), "leaveTypeId", 1, "startDate", "2030-06-10",
                "endDate", "2030-06-11", "reason", "Family event")).body().get("id").asLong();
        assertThat(hr.post("/api/v1/leave-requests/" + leave + "/approve", Map.of()).status()).isEqualTo(200);

        JsonNode r = hr.get("/api/v1/analytics/staff?from=2030-06-01&to=2030-06-30&departmentId=" + dept).body();
        assertThat(kpi(r, "headcount")).as("two nurses and the department's doctor").isEqualByComparingTo("3");
        assertThat(kpi(r, "attendanceRate")).as("(1 + 0.5) / 2").isEqualByComparingTo("75.0");
        assertThat(kpi(r, "leaveDays")).isEqualByComparingTo("2");
        assertThat(kpi(r, "joiners")).isEqualByComparingTo("0");
        assertThat(r.at("/breakdowns/0/rows/0/label").asText()).isEqualTo("NURSE");
        assertThat(r.at("/breakdowns/0/rows/0/value").asInt()).isEqualTo(2);
        assertThat(admin.get("/api/v1/analytics/staff").status()).isEqualTo(200);
        assertThat(desk.get("/api/v1/analytics/staff").status()).isEqualTo(403);
    }

    @Test
    void dashboardCardsFollowPermissions() throws Exception {
        long patient = Fixtures.patient(desk);
        Fixtures.book(desk, patient, doctorId, "2030-06-03", "09:00");

        JsonNode mine = doctor.get("/api/v1/analytics/dashboard").body();
        assertThat(kpi(mine, "myToday")).isEqualByComparingTo("1");
        assertThat(hasKpi(mine, "collectedToday")).isFalse();
        assertThat(mine.at("/trends/0/points")).hasSize(14);

        JsonNode pharmacist = api.as("PHARMACIST").get("/api/v1/analytics/dashboard").body();
        assertThat(hasKpi(pharmacist, "pharmacyQueue")).isTrue();
        assertThat(hasKpi(pharmacist, "lowStock")).isTrue();
        assertThat(hasKpi(pharmacist, "outstanding")).isFalse();
        assertThat(hasKpi(pharmacist, "myToday")).isFalse();

        JsonNode accountant = api.as("ACCOUNTANT").get("/api/v1/analytics/dashboard").body();
        BigDecimal outstanding = jdbc.queryForObject("SELECT COALESCE(SUM(balance_due), 0) FROM invoices WHERE status IN ('ISSUED','PARTIALLY_PAID')", BigDecimal.class);
        assertThat(kpi(accountant, "outstanding")).isEqualByComparingTo(outstanding);
        assertThat(hasKpi(accountant, "labPending")).isFalse();
    }
}
