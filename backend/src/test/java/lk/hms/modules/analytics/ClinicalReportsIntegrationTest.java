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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Each test works in a department of its own, so department-filtered figures are exact. Clock: Mon 2030-06-03 08:30 Colombo. */
@IntegrationTest
class ClinicalReportsIntegrationTest {

    @Autowired Api api;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;

    Api.Session admin, desk, doctor;
    long dept, doctorId, patientId, encounterId;
    String q;

    @BeforeEach
    void setUp() throws Exception {
        clock.reset();
        admin = api.as("HOSPITAL_ADMIN");
        desk = api.as("RECEPTIONIST");
        doctor = api.as("DOCTOR");
        dept = Fixtures.department(admin);
        doctorId = Fixtures.doctor(admin, dept, doctor.user.getId());
        Fixtures.schedule(admin, doctorId, 1, "09:00", "12:00", 15, 4);
        patientId = Fixtures.patient(desk);
        long apt = Fixtures.book(desk, patientId, doctorId, "2030-06-03", "09:00").body().get("id").asLong();
        desk.post("/api/v1/appointments/" + apt + "/check-in", null);
        doctor.post("/api/v1/appointments/" + apt + "/start", null);
        encounterId = doctor.get("/api/v1/patients/" + patientId + "/emr").body().at("/encounters/0/id").asLong();
        q = "?from=2030-06-01&to=2030-06-30&departmentId=" + dept;
    }

    private static BigDecimal kpi(JsonNode report, String key) {
        for (JsonNode k : report.get("kpis")) if (k.get("key").asText().equals(key)) return k.get("value").decimalValue();
        throw new AssertionError("no kpi " + key);
    }

    @Test
    void occupancyAndLengthOfStay() throws Exception {
        long ward = admin.post("/api/v1/wards", Map.of("code", "R" + Fixtures.uid(), "name", "Report ward " + Fixtures.uid(), "departmentId", dept,
                "wardType", "GENERAL", "genderPolicy", "MIXED")).body().get("id").asLong();
        long bed = admin.post("/api/v1/wards/" + ward + "/rooms", Map.of("roomNo", "1", "roomType", "SHARED", "dailyRate", "1000.00", "beds", 2))
                .body().at("/rooms/0/beds/0/id").asLong();
        long adm = doctor.post("/api/v1/admissions", Map.of("patientId", patientId, "attendingDoctorId", doctorId, "admissionType", "EMERGENCY",
                "reason", "Observation")).body().get("id").asLong();
        desk.post("/api/v1/admissions/" + adm + "/admit", Map.of("bedId", bed));
        clock.advance(Duration.ofHours(24));
        desk.post("/api/v1/admissions/" + adm + "/discharge", Map.of("dischargeType", "ROUTINE", "summary", "Well after a day of observation."));

        JsonNode r = admin.get("/api/v1/analytics/inpatient" + q).body();
        // range so far: 2030-05-31 18:30Z to now 2030-06-04 03:00Z = 80.5 h; 2 beds; one 24 h stay
        assertThat(kpi(r, "occupancy")).isEqualByComparingTo("14.9");
        assertThat(kpi(r, "alos")).isEqualByComparingTo("1.0");
        assertThat(kpi(r, "admissions")).isEqualByComparingTo("1");
        assertThat(kpi(r, "discharges")).isEqualByComparingTo("1");
        assertThat(kpi(r, "inpatients")).isEqualByComparingTo("0");
        assertThat(r.at("/breakdowns/0/rows/0/value").decimalValue()).isEqualByComparingTo("14.9");
    }

    @Test
    void laboratoryTurnaroundAndBacklog() throws Exception {
        Api.Session lab = api.as("LAB_STAFF");
        long hb = jdbc.queryForObject("SELECT id FROM lab_tests WHERE code = 'HB'", Long.class);    // target 4 h
        long ufr = jdbc.queryForObject("SELECT id FROM lab_tests WHERE code = 'UFR'", Long.class);
        JsonNode req = doctor.post("/api/v1/encounters/" + encounterId + "/lab-requests", Map.of("testIds", List.of(hb, ufr), "priority", "URGENT")).body();
        long hbItem = req.at("/items/0/id").asLong();
        lab.post("/api/v1/lab-items/" + hbItem + "/collect-sample", null);
        lab.post("/api/v1/lab-items/" + hbItem + "/start-processing", null);
        clock.advance(Duration.ofHours(2));
        lab.post("/api/v1/lab-items/" + hbItem + "/result", Map.of("numericValue", 9.8));

        JsonNode r = admin.get("/api/v1/analytics/laboratory" + q).body();
        assertThat(kpi(r, "tests")).isEqualByComparingTo("2");
        assertThat(kpi(r, "withinTarget")).isEqualByComparingTo("100.0");
        assertThat(kpi(r, "avgTurnaround")).isEqualByComparingTo("2.0");
        assertThat(kpi(r, "backlog")).as("UFR still waiting").isEqualByComparingTo("1");
        assertThat(kpi(r, "abnormal")).as("9.8 g/dL is low").isEqualByComparingTo("100.0");
        assertThat(r.at("/breakdowns/1/rows/0/label").asText()).isEqualTo("URGENT");
    }

    @Test
    void pharmacyConsumptionAndStockPosition() throws Exception {
        Api.Session pharmacist = api.as("PHARMACIST");
        long category = pharmacist.post("/api/v1/medicine-categories", Map.of("name", "Cat " + Fixtures.uid())).body().get("id").asLong();
        String code = "RPT" + Fixtures.uid();
        Map<String, Object> med = new HashMap<>(Map.of("code", code, "genericName", "Reportamol " + Fixtures.uid(), "categoryId", category,
                "dosageForm", "TABLET", "strength", "500 mg", "unit", "tablet", "unitPrice", "4.25", "reorderLevel", 50));
        med.put("prescriptionRequired", true);
        long medicineId = pharmacist.post("/api/v1/medicines", med).body().get("id").asLong();
        pharmacist.post("/api/v1/stock/receipts", Map.of("medicineId", medicineId, "batchNo", "SOON", "expiryDate", "2030-07-15", "quantity", 50, "costPrice", "2.00"));
        JsonNode rx = doctor.post("/api/v1/encounters/" + encounterId + "/prescriptions", Map.of("items", List.of(
                Map.of("medicineId", medicineId, "dose", "1 tablet", "frequency", "BD", "route", "ORAL", "durationDays", 5, "quantity", 10)))).body();
        pharmacist.post("/api/v1/prescriptions/" + rx.get("id").asLong() + "/dispense",
                Map.of("items", List.of(Map.of("prescriptionItemId", rx.at("/items/0/id").asLong(), "quantity", 10))));

        JsonNode r = admin.get("/api/v1/analytics/pharmacy" + q).body();
        assertThat(kpi(r, "prescriptions")).isEqualByComparingTo("1");
        assertThat(kpi(r, "units")).isEqualByComparingTo("10");
        assertThat(kpi(r, "value")).isEqualByComparingTo("42.50");
        assertThat(r.at("/tables/0/rows").toString()).as("40 left, below 50").contains("Reportamol");
        assertThat(r.at("/tables/1/rows").toString()).as("expires 2030-07-15, inside 90 days").contains("SOON");

        Long units = jdbc.queryForObject("""
                SELECT COALESCE(SUM(di.quantity), 0) FROM dispensation_items di JOIN dispensations d ON d.id = di.dispensation_id
                WHERE d.dispensed_at >= '2030-05-31 18:30:00' AND d.dispensed_at < '2030-06-30 18:30:00'""", Long.class);
        JsonNode all = admin.get("/api/v1/analytics/pharmacy?from=2030-06-01&to=2030-06-30").body();
        assertThat(kpi(all, "units")).isEqualByComparingTo(BigDecimal.valueOf(units));
    }
}
