package lk.hms.journey;

import com.fasterxml.jackson.databind.JsonNode;
import lk.hms.support.Api;
import lk.hms.support.Fixtures;
import lk.hms.support.IntegrationTest;
import lk.hms.support.MutableClock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One patient through every Phase 4 module, each step done by the role that does it in the hospital.
 * Every charge raised on the way must land on a single invoice that is then paid in full.
 */
@IntegrationTest
class PatientJourneyIntegrationTest {

    @Autowired Api api;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;

    @Test
    void outpatientToDischargeToPaidInvoice() throws Exception {
        clock.reset();
        Api.Session admin = api.as("HOSPITAL_ADMIN");
        Api.Session desk = api.as("RECEPTIONIST");
        Api.Session doctor = api.as("DOCTOR");
        Api.Session lab = api.as("LAB_STAFF");
        Api.Session pharmacist = api.as("PHARMACIST");
        Api.Session accountant = api.as("ACCOUNTANT");

        // setup: department, doctor (fee 2500), ward with a 5000/day bed, a medicine in stock
        long dept = Fixtures.department(admin);
        long doctorId = Fixtures.doctor(admin, dept, doctor.user.getId());
        Fixtures.schedule(admin, doctorId, 1, "09:00", "12:00", 15, 4);
        long wardId = admin.post("/api/v1/wards", Map.of("code", "J" + Fixtures.uid(), "name", "Journey ward " + Fixtures.uid(), "departmentId", dept,
                "wardType", "GENERAL", "genderPolicy", "MIXED")).body().get("id").asLong();
        long bedId = admin.post("/api/v1/wards/" + wardId + "/rooms", Map.of("roomNo", "J1", "roomType", "PRIVATE", "dailyRate", "5000.00", "beds", 1))
                .body().at("/rooms/0/beds/0/id").asLong();
        long category = pharmacist.post("/api/v1/medicine-categories", Map.of("name", "Cat " + Fixtures.uid())).body().get("id").asLong();
        Map<String, Object> med = new HashMap<>(Map.of("code", "J" + Fixtures.uid(), "genericName", "Paracetamol", "categoryId", category,
                "dosageForm", "TABLET", "strength", "500 mg", "unit", "tablet", "unitPrice", "4.25", "reorderLevel", 2));
        med.put("prescriptionRequired", true);
        long medicineId = pharmacist.post("/api/v1/medicines", med).body().get("id").asLong();
        pharmacist.post("/api/v1/stock/receipts", Map.of("medicineId", medicineId, "batchNo", "J-1", "expiryDate", "2031-01-31", "quantity", 50, "costPrice", "2.00"));
        long hb = jdbc.queryForObject("SELECT id FROM lab_tests WHERE code='HB'", Long.class);

        // 1. front desk registers and books; doctor sees the patient
        long patientId = Fixtures.patient(desk);
        long apt = Fixtures.book(desk, patientId, doctorId, "2030-06-03", "09:00").body().get("id").asLong();
        desk.post("/api/v1/appointments/" + apt + "/check-in", null);
        doctor.post("/api/v1/appointments/" + apt + "/start", null);
        long encounterId = doctor.get("/api/v1/patients/" + patientId + "/emr").body().at("/encounters/0/id").asLong();

        // 2. diagnosis, lab order, prescription; consultation completes
        doctor.post("/api/v1/encounters/" + encounterId + "/diagnoses",
                Map.of("icd10Code", "A90", "description", "Dengue fever", "type", "PRIMARY", "certainty", "PROVISIONAL"));
        long hbItem = doctor.post("/api/v1/encounters/" + encounterId + "/lab-requests", Map.of("testIds", List.of(hb), "priority", "URGENT"))
                .body().at("/items/0/id").asLong();
        JsonNode rx = doctor.post("/api/v1/encounters/" + encounterId + "/prescriptions", Map.of("items", List.of(
                Map.of("medicineId", medicineId, "dose", "1 tablet", "frequency", "QDS", "route", "ORAL", "durationDays", 3, "quantity", 10)))).body();
        assertThat(doctor.post("/api/v1/appointments/" + apt + "/complete", null).status()).isEqualTo(200);

        // 3. lab works the sample; doctor reviews
        lab.post("/api/v1/lab-items/" + hbItem + "/collect-sample", null);
        lab.post("/api/v1/lab-items/" + hbItem + "/start-processing", null);
        assertThat(lab.post("/api/v1/lab-items/" + hbItem + "/result", Map.of("numericValue", 11.2)).body().at("/items/0/result/flag").asText()).isEqualTo("L");
        doctor.post("/api/v1/lab-items/" + hbItem + "/review", null);

        // 4. pharmacy dispenses in full
        Api.Response dispensed = pharmacist.post("/api/v1/prescriptions/" + rx.get("id").asLong() + "/dispense",
                Map.of("items", List.of(Map.of("prescriptionItemId", rx.at("/items/0/id").asLong(), "quantity", 10))));
        assertThat(dispensed.status()).as(dispensed.toString()).isEqualTo(200);

        // 5. doctor requests admission, desk admits; 26 hours later the patient is discharged
        long admission = doctor.post("/api/v1/admissions", Map.of("patientId", patientId, "attendingDoctorId", doctorId,
                "admissionType", "EMERGENCY", "reason", "Dengue with falling platelets")).body().get("id").asLong();
        desk.post("/api/v1/admissions/" + admission + "/admit", Map.of("bedId", bedId));
        clock.advance(Duration.ofHours(26));
        assertThat(desk.post("/api/v1/admissions/" + admission + "/discharge", Map.of("dischargeType", "ROUTINE",
                "summary", "Platelets recovered, afebrile 48 hours. Review in clinic in one week.")).body().get("status").asText()).isEqualTo("DISCHARGED");

        // 6. accountant bills everything on one invoice and takes payment
        JsonNode charges = accountant.get("/api/v1/patients/" + patientId + "/unbilled-charges").body();
        assertThat(charges.findValuesAsText("category")).containsExactlyInAnyOrder("CONSULTATION", "LABORATORY", "PHARMACY", "ADMISSION");
        List<Long> ids = new ArrayList<>();
        charges.forEach(c -> ids.add(c.get("id").asLong()));
        long invoiceId = accountant.post("/api/v1/invoices", Map.of("patientId", patientId, "chargeIds", ids)).body().get("id").asLong();
        JsonNode issued = accountant.post("/api/v1/invoices/" + invoiceId + "/issue", null).body();
        // 2500 consultation + 450 HB + 10 x 4.25 tablets + 2 bed-days x 5000
        assertThat(issued.get("totalAmount").decimalValue()).isEqualByComparingTo(new BigDecimal("12992.50"));
        JsonNode paid = accountant.post("/api/v1/invoices/" + invoiceId + "/payments", Map.of("amount", "12992.50", "method", "CASH")).body();
        assertThat(paid.get("status").asText()).isEqualTo("PAID");
        assertThat(paid.get("balanceDue").decimalValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(accountant.get("/api/v1/patients/" + patientId + "/unbilled-charges").body()).isEmpty();

        // the trail: every module wrote audit rows for this journey
        Integer audited = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT entity_type) FROM audit_logs
                WHERE entity_type IN ('APPOINTMENT','LAB_REQUEST','DISPENSATION','ADMISSION','INVOICE')
                """, Integer.class);
        assertThat(audited).isGreaterThanOrEqualTo(4);
    }
}
