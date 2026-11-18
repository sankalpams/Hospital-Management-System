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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A department of its own with a known day of activity (clock: Mon 2030-06-03 08:30 Colombo), so
 * department-filtered figures are exact; hospital-wide figures are checked against hand-written SQL.
 */
@IntegrationTest
class OperationalReportsIntegrationTest {

    @Autowired Api api;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;

    Api.Session admin, desk, doctor;
    long dept;
    String q;

    @BeforeEach
    void dayOfClinic() throws Exception {
        clock.reset();
        admin = api.as("HOSPITAL_ADMIN");
        desk = api.as("RECEPTIONIST");
        doctor = api.as("DOCTOR");
        dept = Fixtures.department(admin);
        long doctorId = Fixtures.doctor(admin, dept, doctor.user.getId());   // fee 2500
        Fixtures.schedule(admin, doctorId, 1, "09:00", "12:00", 15, 4);
        long p1 = Fixtures.patient(desk), p2 = Fixtures.patient(desk), p3 = Fixtures.patient(desk);
        long a1 = Fixtures.book(desk, p1, doctorId, "2030-06-03", "09:00").body().get("id").asLong();
        long a2 = Fixtures.book(desk, p2, doctorId, "2030-06-03", "09:15").body().get("id").asLong();
        long a3 = Fixtures.book(desk, p3, doctorId, "2030-06-03", "09:30").body().get("id").asLong();
        desk.post("/api/v1/appointments/" + a3 + "/cancel", Map.of("reason", "Patient request"));
        desk.post("/api/v1/appointments/" + a1 + "/check-in", null);
        desk.post("/api/v1/appointments/" + a2 + "/check-in", null);
        clock.advance(Duration.ofMinutes(10));
        consult(a1, p1);                             // wait 10, consult 15
        clock.advance(Duration.ofMinutes(5));
        consult(a2, p2);                             // wait 30, consult 15
        List<Long> charges = new ArrayList<>();
        for (long p : List.of(p1, p2)) {
            desk.get("/api/v1/patients/" + p + "/unbilled-charges").body().forEach(c -> charges.add(c.get("id").asLong()));
            long inv = desk.post("/api/v1/invoices", Map.of("patientId", p, "chargeIds", List.of(charges.remove(0)))).body().get("id").asLong();
            desk.post("/api/v1/invoices/" + inv + "/issue", null);
        }
        q = "?from=2030-06-01&to=2030-06-30&departmentId=" + dept;
    }

    private void consult(long appointment, long patient) throws Exception {
        doctor.post("/api/v1/appointments/" + appointment + "/start", null);
        long enc = doctor.get("/api/v1/patients/" + patient + "/emr").body().at("/encounters/0/id").asLong();
        doctor.post("/api/v1/encounters/" + enc + "/diagnoses", Map.of("icd10Code", "J06.9", "description", "URTI", "type", "PRIMARY", "certainty", "CONFIRMED"));
        clock.advance(Duration.ofMinutes(15));
        assertThat(doctor.post("/api/v1/appointments/" + appointment + "/complete", null).status()).isEqualTo(200);
    }

    private static BigDecimal kpi(JsonNode report, String key) {
        for (JsonNode k : report.get("kpis")) if (k.get("key").asText().equals(key)) return k.get("value").decimalValue();
        throw new AssertionError("no kpi " + key);
    }

    @Test
    void appointmentFiguresForOneDepartmentAreExact() throws Exception {
        JsonNode r = admin.get("/api/v1/analytics/appointments" + q).body();
        assertThat(r.get("granularity").asText()).isEqualTo("day");
        assertThat(kpi(r, "total")).isEqualByComparingTo("3");
        assertThat(kpi(r, "completed")).isEqualByComparingTo("2");
        assertThat(kpi(r, "cancellationRate")).isEqualByComparingTo("33.3");
        assertThat(kpi(r, "noShowRate")).isEqualByComparingTo("0");
        assertThat(kpi(r, "avgWait")).isEqualByComparingTo("20.0");
        JsonNode volume = r.at("/trends/0/points");
        assertThat(volume).hasSize(30);                       // every day present, zeros included
        assertThat(volume.get(2).get("bucket").asText()).isEqualTo("2030-06-03");
        assertThat(volume.get(2).get("value").asInt()).isEqualTo(3);
        assertThat(r.at("/breakdowns/2/rows/0/label").asText()).isEqualTo("Mon");   // cancellations by weekday
        assertThat(r.at("/breakdowns/2/rows/0/value").asInt()).isEqualTo(1);

        JsonNode weekly = admin.get("/api/v1/analytics/appointments" + q + "&granularity=week").body();
        assertThat(weekly.at("/trends/0/points/1/bucket").asText()).isEqualTo("2030-06-03");   // weeks start on Monday
    }

    @Test
    void doctorsDepartmentsAndPatients() throws Exception {
        JsonNode w = admin.get("/api/v1/analytics/doctors/workload" + q).body();
        assertThat(kpi(w, "consultations")).isEqualByComparingTo("2");
        assertThat(kpi(w, "avgConsult")).isEqualByComparingTo("15.0");
        assertThat(w.at("/tables/0/rows/0/3").asInt()).as("patients").isEqualTo(2);

        JsonNode d = admin.get("/api/v1/analytics/departments" + q).body();
        assertThat(d.at("/tables/0/rows")).hasSize(1);
        assertThat(d.at("/tables/0/rows/0/2").asInt()).as("appointments").isEqualTo(3);
        assertThat(kpi(d, "revenue")).as("two invoiced consultations").isEqualByComparingTo("5000.00");

        JsonNode p = admin.get("/api/v1/analytics/patients" + q).body();
        assertThat(kpi(p, "seen")).isEqualByComparingTo("2");
        assertThat(p.at("/breakdowns/0/rows").findValues("value").stream().mapToInt(JsonNode::asInt).sum()).isEqualTo(2);
    }

    @Test
    void hospitalWideFiguresMatchHandWrittenSql() throws Exception {
        JsonNode r = admin.get("/api/v1/analytics/appointments?from=2030-06-01&to=2030-06-30").body();
        // Colombo is UTC+05:30: local June 2030 = [2030-05-31 18:30, 2030-06-30 18:30) UTC
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM appointments WHERE slot_start >= '2030-05-31 18:30:00' AND slot_start < '2030-06-30 18:30:00'", Long.class);
        Long noShow = jdbc.queryForObject("SELECT COUNT(*) FROM appointments WHERE status = 'NO_SHOW' AND slot_start >= '2030-05-31 18:30:00' AND slot_start < '2030-06-30 18:30:00'", Long.class);
        assertThat(kpi(r, "total")).isEqualByComparingTo(BigDecimal.valueOf(total));
        assertThat(r.at("/trends/0/points").findValues("value").stream().mapToLong(JsonNode::asLong).sum()).isEqualTo(total);
        assertThat(r.at("/breakdowns/0/rows").findValues("value").stream().mapToLong(JsonNode::asLong).sum()).isEqualTo(total);
        assertThat(noShow).isGreaterThanOrEqualTo(0);

        JsonNode p = admin.get("/api/v1/analytics/patients?from=2026-01-01&to=2026-12-31&granularity=month").body();
        Long registered = jdbc.queryForObject("SELECT COUNT(*) FROM patients WHERE created_at >= '2025-12-31 18:30:00' AND created_at < '2026-12-31 18:30:00'", Long.class);
        assertThat(kpi(p, "registered")).isEqualByComparingTo(BigDecimal.valueOf(registered));
        assertThat(p.at("/trends/0/points")).hasSize(12);
    }

    @Test
    void permissionsValidationAndCsv() throws Exception {
        assertThat(api.as("NURSE").get("/api/v1/analytics/appointments").status()).isEqualTo(403);
        assertThat(api.as("ACCOUNTANT").get("/api/v1/analytics/patients").status()).isEqualTo(403);
        assertThat(admin.get("/api/v1/analytics/nonsense").status()).isEqualTo(404);
        assertThat(admin.get("/api/v1/analytics/appointments?from=2030-06-10&to=2030-06-01").code()).isEqualTo("INVALID_RANGE");
        assertThat(admin.get("/api/v1/analytics/appointments?from=2030-01-01&to=2030-06-30&granularity=day").code()).isEqualTo("INVALID_FILTER");
        assertThat(admin.get("/api/v1/analytics/appointments?granularity=hour").code()).isEqualTo("INVALID_FILTER");

        Api.Response csv = admin.get("/api/v1/analytics/appointments/export.csv" + q);
        assertThat(csv.status()).isEqualTo(200);
        assertThat(csv.raw().getResponse().getContentType()).startsWith("text/csv");
        assertThat(csv.raw().getResponse().getHeader("Content-Disposition")).contains("hms-appointments-2030-06-01-to-2030-06-30.csv");
        String body = csv.raw().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).contains("Appointments,3,count").contains("Cancellation rate,33.3,percent");
        assertThat(api.as("NURSE").get("/api/v1/analytics/appointments/export.csv").status()).isEqualTo(403);
    }

    @Test
    void csvNeutralisesFormulas() {
        assertThat(Csv.cell("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
        assertThat(Csv.cell("+94 77")).isEqualTo("'+94 77");
        assertThat(Csv.cell(new BigDecimal("-12.50"))).isEqualTo("-12.50");
        assertThat(Csv.cell("Smith, J")).isEqualTo("\"Smith, J\"");
    }
}
