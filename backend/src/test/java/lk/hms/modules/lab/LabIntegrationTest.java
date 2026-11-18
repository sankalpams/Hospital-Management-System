package lk.hms.modules.lab;

import com.fasterxml.jackson.databind.JsonNode;
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

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class LabIntegrationTest {

    @Autowired Api api;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;

    Api.Session admin, desk, doctor, lab;
    long patientId, encounterId, hb, ufr;

    @BeforeEach
    void openEncounter() throws Exception {
        clock.reset();
        admin = api.as("HOSPITAL_ADMIN");
        desk = api.as("RECEPTIONIST");
        doctor = api.as("DOCTOR");
        lab = api.as("LAB_STAFF");
        long doctorId = Fixtures.doctor(admin, Fixtures.department(admin), doctor.user.getId());
        Fixtures.schedule(admin, doctorId, 1, "09:00", "12:00", 15, 4);
        patientId = Fixtures.patient(desk);
        long apt = Fixtures.book(desk, patientId, doctorId, "2030-06-03", "09:00").body().get("id").asLong();
        desk.post("/api/v1/appointments/" + apt + "/check-in", null);
        doctor.post("/api/v1/appointments/" + apt + "/start", null);
        encounterId = doctor.get("/api/v1/patients/" + patientId + "/emr").body().at("/encounters/0/id").asLong();
        hb = jdbc.queryForObject("SELECT id FROM lab_tests WHERE code='HB'", Long.class);     // 12.0-16.0 g/dL, 450.00
        ufr = jdbc.queryForObject("SELECT id FROM lab_tests WHERE code='UFR'", Long.class);   // text result
    }

    private JsonNode order(String priority) throws Exception {
        Api.Response r = doctor.post("/api/v1/encounters/" + encounterId + "/lab-requests", Map.of("testIds", List.of(hb, ufr), "priority", priority));
        assertThat(r.status()).as(r.toString()).isEqualTo(201);
        return r.body();
    }

    @Test
    void fullWorkflowFlagsResultNotifiesDoctorAndChargesEachTest() throws Exception {
        JsonNode req = order("ROUTINE");
        assertThat(req.get("requestNo").asText()).matches("LAB-2030-\\d{6}");
        long hbItem = req.at("/items/0/id").asLong();
        assertThat(desk.get("/api/v1/patients/" + patientId + "/unbilled-charges").body().findValuesAsText("category"))
                .containsExactly("LABORATORY", "LABORATORY");

        assertThat(lab.post("/api/v1/lab-items/" + hbItem + "/result", Map.of("numericValue", 10)).code())
                .as("cannot skip steps").isEqualTo("INVALID_STATUS_TRANSITION");
        JsonNode collected = lab.post("/api/v1/lab-items/" + hbItem + "/collect-sample", null).body();
        assertThat(collected.at("/items/0/sampleBarcode").asText()).matches("SMP-2030-\\d{6}");
        lab.post("/api/v1/lab-items/" + hbItem + "/start-processing", null);
        JsonNode done = lab.post("/api/v1/lab-items/" + hbItem + "/result", Map.of("numericValue", 9.8, "comments", "Repeat advised")).body();
        assertThat(done.at("/items/0/status").asText()).isEqualTo("COMPLETED");
        assertThat(done.at("/items/0/result/flag").asText()).isEqualTo("L");
        assertThat(done.get("status").asText()).as("overall = least advanced").isEqualTo("REQUESTED");

        JsonNode notes = doctor.get("/api/v1/notifications?unread=true").body();
        assertThat(notes.get("content").findValuesAsText("title")).contains("Abnormal result: Haemoglobin");

        assertThat(lab.post("/api/v1/lab-items/" + hbItem + "/review", null).status()).as("lab staff cannot review").isEqualTo(403);
        assertThat(doctor.post("/api/v1/lab-items/" + hbItem + "/review", null).body().at("/items/0/status").asText()).isEqualTo("REVIEWED");
    }

    @Test
    void textResultsAreUnflaggedAndValueIsRequired() throws Exception {
        long item = order("ROUTINE").at("/items/1/id").asLong();
        lab.post("/api/v1/lab-items/" + item + "/collect-sample", null);
        lab.post("/api/v1/lab-items/" + item + "/start-processing", null);
        assertThat(lab.post("/api/v1/lab-items/" + item + "/result", Map.of()).code()).isEqualTo("VALUE_REQUIRED");
        JsonNode r = lab.post("/api/v1/lab-items/" + item + "/result", Map.of("textValue", "Pus cells 2-4/hpf")).body();
        assertThat(r.at("/items/1/result/flag").isMissingNode()).as("null fields are omitted").isTrue();
    }

    @Test
    void cancellingAnUnstartedTestRemovesItsCharge() throws Exception {
        long item = order("ROUTINE").at("/items/0/id").asLong();
        assertThat(lab.post("/api/v1/lab-items/" + item + "/cancel", Map.of("reason", "Duplicate")).status()).isEqualTo(403);
        JsonNode r = doctor.post("/api/v1/lab-items/" + item + "/cancel", Map.of("reason", "Ordered twice")).body();
        assertThat(r.at("/items/0/status").asText()).isEqualTo("CANCELLED");
        assertThat(desk.get("/api/v1/patients/" + patientId + "/unbilled-charges").body()).hasSize(1);
    }

    @Test
    void urgentOrdersAlertTheLabAndWorklistFiltersByStatus() throws Exception {
        order("STAT");
        assertThat(lab.get("/api/v1/notifications?unread=true").body().get("content").findValuesAsText("title").stream()
                .anyMatch(t -> t.startsWith("STAT lab request"))).isTrue();
        JsonNode worklist = lab.get("/api/v1/lab-requests?status=REQUESTED&patientId=" + patientId).body();
        assertThat(worklist.get("totalElements").asInt()).isEqualTo(1);
        assertThat(api.as("PHARMACIST").get("/api/v1/lab-requests").status()).isEqualTo(403);
    }

    @Test
    void onlyTheTreatingDoctorOrders() throws Exception {
        Api.Session other = api.as("DOCTOR");
        Fixtures.doctor(admin, Fixtures.department(admin), other.user.getId());
        assertThat(other.post("/api/v1/encounters/" + encounterId + "/lab-requests", Map.of("testIds", List.of(hb), "priority", "ROUTINE")).code())
                .isEqualTo("NOT_TREATING_DOCTOR");
    }
}
