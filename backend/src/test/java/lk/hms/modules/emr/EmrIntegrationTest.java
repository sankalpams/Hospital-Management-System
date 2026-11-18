package lk.hms.modules.emr;

import lk.hms.support.Api;
import lk.hms.support.Fixtures;
import lk.hms.support.IntegrationTest;
import lk.hms.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static lk.hms.support.Fixtures.book;
import static org.assertj.core.api.Assertions.assertThat;

/** Clock: Monday 2030-06-03 08:30 Colombo. Each test starts a fresh consultation at 09:00 today. */
@IntegrationTest
class EmrIntegrationTest {

    @Autowired Api api;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;

    Api.Session admin, desk, doctor;
    long patientId, appointmentId, encounterId;

    @BeforeEach
    void consultationInProgress() throws Exception {
        clock.reset();
        admin = api.as("HOSPITAL_ADMIN");
        desk = api.as("RECEPTIONIST");
        doctor = api.as("DOCTOR");
        long doctorId = Fixtures.doctor(admin, Fixtures.department(admin), doctor.user.getId());
        Fixtures.schedule(admin, doctorId, 1, "09:00", "12:00", 15, 4);
        patientId = Fixtures.patient(desk);
        appointmentId = book(desk, patientId, doctorId, "2030-06-03", "09:00").body().get("id").asLong();
        desk.post("/api/v1/appointments/" + appointmentId + "/check-in", null);
        assertThat(doctor.post("/api/v1/appointments/" + appointmentId + "/start", null).status()).isEqualTo(200);
        encounterId = doctor.get("/api/v1/patients/" + patientId + "/emr").body().at("/encounters/0/id").asLong();
    }

    static long medicine(JdbcTemplate jdbc, String generic) {
        jdbc.update("INSERT IGNORE INTO medicine_categories(name) VALUES ('Test category')");
        Long cat = jdbc.queryForObject("SELECT id FROM medicine_categories WHERE name='Test category'", Long.class);
        String code = "M" + Fixtures.uid();
        jdbc.update("INSERT INTO medicines(code, generic_name, category_id, dosage_form, strength, unit, unit_price) VALUES (?,?,?,?,?,?,?)",
                code, generic, cat, "CAPSULE", "500 mg", "capsule", 12.50);
        return jdbc.queryForObject("SELECT id FROM medicines WHERE code=?", Long.class, code);
    }

    static Map<String, Object> rxItem(long medicineId) {
        return Map.of("medicineId", medicineId, "dose", "1 capsule", "frequency", "TDS", "route", "ORAL", "durationDays", 5, "quantity", 15);
    }

    @Test
    void startingAConsultationOpensAnEncounterAndCompletionNeedsADiagnosis() throws Exception {
        Api.Response emr = doctor.get("/api/v1/patients/" + patientId + "/emr");
        assertThat(emr.body().at("/encounters/0/status").asText()).isEqualTo("OPEN");
        assertThat(emr.body().at("/encounters/0/canWrite").asBoolean()).isTrue();
        assertThat(emr.body().at("/encounters/0/encounterNo").asText()).matches("ENC-2030-\\d{6}");

        assertThat(doctor.post("/api/v1/appointments/" + appointmentId + "/complete", null).code()).isEqualTo("DIAGNOSIS_REQUIRED");
        Api.Response dx = doctor.post("/api/v1/encounters/" + encounterId + "/diagnoses",
                Map.of("icd10Code", "J45.9", "description", "Asthma, unspecified", "type", "PRIMARY", "certainty", "CONFIRMED"));
        assertThat(dx.status()).isEqualTo(201);
        assertThat(doctor.post("/api/v1/appointments/" + appointmentId + "/complete", null).status()).isEqualTo(200);

        assertThat(doctor.get("/api/v1/encounters/" + encounterId).body().get("status").asText()).isEqualTo("CLOSED");
        assertThat(doctor.post("/api/v1/encounters/" + encounterId + "/diagnoses",
                Map.of("description", "Late", "type", "SECONDARY", "certainty", "PROVISIONAL")).code()).isEqualTo("ENCOUNTER_CLOSED");
    }

    @Test
    void onlyTheTreatingDoctorWritesAndOthersNeedBreakTheGlassToRead() throws Exception {
        Api.Session other = api.as("DOCTOR");
        Fixtures.doctor(admin, Fixtures.department(admin), other.user.getId());
        assertThat(other.post("/api/v1/encounters/" + encounterId + "/diagnoses",
                Map.of("description", "X", "type", "PRIMARY", "certainty", "PROVISIONAL")).code()).isEqualTo("NOT_TREATING_DOCTOR");

        assertThat(other.get("/api/v1/patients/" + patientId + "/emr").code()).isEqualTo("BREAK_GLASS_REQUIRED");
        assertThat(other.get("/api/v1/patients/" + patientId + "/emr?reason=Emergency%20cover%20for%20ward").status()).isEqualTo(200);
        Integer audited = jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit_logs WHERE action='VIEW_SENSITIVE' AND entity_id=? AND actor_user_id=? AND summary='Break-the-glass access'
                """, Integer.class, String.valueOf(patientId), other.user.getId());
        assertThat(audited).isEqualTo(1);

        // Administrators and front desk have no clinical access at all.
        assertThat(api.as("HOSPITAL_ADMIN").get("/api/v1/patients/" + patientId + "/emr").status()).isEqualTo(403);
        assertThat(desk.get("/api/v1/patients/" + patientId + "/emr").status()).isEqualTo(403);
    }

    @Test
    void notesAreEditableUntilSignedThenOnlyAddenda() throws Exception {
        long noteId = doctor.post("/api/v1/encounters/" + encounterId + "/notes",
                Map.of("noteType", "DOCTOR_NOTE", "content", "Wheeze on exam.")).body().get("id").asLong();
        assertThat(doctor.put("/api/v1/notes/" + noteId, Map.of("content", "Bilateral wheeze on exam.")).status()).isEqualTo(200);
        assertThat(doctor.post("/api/v1/notes/" + noteId + "/addenda", Map.of("content", "x")).code()).isEqualTo("NOTE_NOT_SIGNED");
        assertThat(doctor.post("/api/v1/notes/" + noteId + "/sign", null).body().get("signedAt").isNull()).isFalse();
        assertThat(doctor.put("/api/v1/notes/" + noteId, Map.of("content", "Changed")).code()).isEqualTo("NOTE_SIGNED");
        Api.Response add = doctor.post("/api/v1/notes/" + noteId + "/addenda", Map.of("content", "Correction: left side only."));
        assertThat(add.status()).isEqualTo(201);
        assertThat(add.body().get("parentNoteId").asLong()).isEqualTo(noteId);
    }

    @Test
    void nurseRecordsVitalsWithPlausibilityChecksAndBmi() throws Exception {
        Api.Session nurse = api.as("NURSE");
        Api.Response ok = nurse.post("/api/v1/patients/" + patientId + "/vitals", Map.of("encounterId", encounterId,
                "temperatureC", 37.2, "pulseBpm", 88, "systolicMmhg", 128, "diastolicMmhg", 82, "weightKg", 70, "heightCm", 175));
        assertThat(ok.status()).as(ok.toString()).isEqualTo(201);
        assertThat(ok.body().get("bmi").decimalValue()).isEqualByComparingTo("22.9");

        assertThat(nurse.post("/api/v1/patients/" + patientId + "/vitals", Map.of("pulseBpm", 900)).status()).isEqualTo(400);
        assertThat(nurse.post("/api/v1/patients/" + patientId + "/vitals", Map.of("systolicMmhg", 80, "diastolicMmhg", 90)).code()).isEqualTo("INVALID_BP");
        assertThat(nurse.post("/api/v1/patients/" + patientId + "/vitals", Map.of()).code()).isEqualTo("EMPTY_VITALS");
        assertThat(nurse.post("/api/v1/encounters/" + encounterId + "/nursing-notes", Map.of("content", "Nebuliser given.")).status()).isEqualTo(201);
        assertThat(nurse.get("/api/v1/patients/" + patientId + "/emr").status()).as("nurse: patient in clinic").isEqualTo(200);
    }

    @Test
    void prescribingAgainstARecordedDrugAllergyNeedsAnOverrideReason() throws Exception {
        long amoxicillin = medicine(jdbc, "Amoxicillin");
        long paracetamol = medicine(jdbc, "Paracetamol");
        assertThat(doctor.post("/api/v1/patients/" + patientId + "/allergies",
                Map.of("allergenType", "DRUG", "allergen", "amoxicillin", "reaction", "Rash", "severity", "SEVERE")).status()).isEqualTo(201);

        Api.Response blocked = doctor.post("/api/v1/encounters/" + encounterId + "/prescriptions", Map.of("items", List.of(rxItem(amoxicillin))));
        assertThat(blocked.code()).isEqualTo("ALLERGY_CONFLICT");
        assertThat(blocked.body().get("message").asText()).contains("amoxicillin (severe) matches Amoxicillin");

        assertThat(doctor.post("/api/v1/encounters/" + encounterId + "/prescriptions",
                Map.of("items", List.of(rxItem(paracetamol)))).status()).as("unrelated drug is fine").isEqualTo(201);

        Api.Response overridden = doctor.post("/api/v1/encounters/" + encounterId + "/prescriptions",
                Map.of("items", List.of(rxItem(amoxicillin)), "allergyOverrideReason", "Desensitised under supervision"));
        assertThat(overridden.status()).isEqualTo(201);
        assertThat(overridden.body().get("prescriptionNo").asText()).matches("RX-2030-\\d{6}");
        assertThat(overridden.body().get("allergyOverrideReason").asText()).isEqualTo("Desensitised under supervision");

        long rxId = overridden.body().get("id").asLong();
        assertThat(doctor.post("/api/v1/prescriptions/" + rxId + "/cancel", null).body().get("status").asText()).isEqualTo("CANCELLED");
        assertThat(doctor.post("/api/v1/encounters/" + encounterId + "/prescriptions",
                Map.of("items", List.of(rxItem(paracetamol), rxItem(paracetamol)))).code()).isEqualTo("DUPLICATE_MEDICINE");
    }
}
