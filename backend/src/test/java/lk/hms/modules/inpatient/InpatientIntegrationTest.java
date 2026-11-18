package lk.hms.modules.inpatient;

import com.fasterxml.jackson.databind.JsonNode;
import lk.hms.support.Api;
import lk.hms.support.Fixtures;
import lk.hms.support.IntegrationTest;
import lk.hms.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class InpatientIntegrationTest {

    @Autowired Api api;
    @Autowired MutableClock clock;

    Api.Session admin, desk, doctor;
    long doctorId, wardId;
    List<Long> beds;

    @BeforeEach
    void ward() throws Exception {
        clock.reset();
        admin = api.as("HOSPITAL_ADMIN");
        desk = api.as("RECEPTIONIST");
        doctor = api.as("DOCTOR");
        long dept = Fixtures.department(admin);
        doctorId = Fixtures.doctor(admin, dept, doctor.user.getId());
        JsonNode w = admin.post("/api/v1/wards", Map.of("code", "W" + Fixtures.uid(), "name", "Ward " + Fixtures.uid(), "departmentId", dept,
                "wardType", "GENERAL", "genderPolicy", "MIXED")).body();
        wardId = w.get("id").asLong();
        JsonNode withRoom = admin.post("/api/v1/wards/" + wardId + "/rooms", Map.of("roomNo", "101", "roomType", "SHARED", "dailyRate", "5000.00", "beds", 2)).body();
        beds = withRoom.at("/rooms/0/beds").findValues("id").stream().map(JsonNode::asLong).toList();
        assertThat(beds).hasSize(2);
    }

    private long requestAdmission(long patientId) throws Exception {
        Api.Response r = doctor.post("/api/v1/admissions", Map.of("patientId", patientId, "attendingDoctorId", doctorId,
                "admissionType", "EMERGENCY", "reason", "Community acquired pneumonia"));
        assertThat(r.status()).as(r.toString()).isEqualTo(201);
        return r.body().get("id").asLong();
    }

    @Test
    void admitTransferDischargeWithBedDayCharges() throws Exception {
        long patient = Fixtures.patient(desk);
        long adm = requestAdmission(patient);
        assertThat(doctor.post("/api/v1/admissions", Map.of("patientId", patient, "attendingDoctorId", doctorId,
                "admissionType", "ELECTIVE", "reason", "Second request")).code()).isEqualTo("ALREADY_ADMITTED");

        JsonNode admitted = desk.post("/api/v1/admissions/" + adm + "/admit", Map.of("bedId", beds.get(0))).body();
        assertThat(admitted.get("status").asText()).isEqualTo("ADMITTED");
        assertThat(admitted.get("currentBed").asText()).endsWith("101-A");
        assertThat(doctor.get("/api/v1/patients/" + patient + "/emr").body().at("/encounters/0/type").asText()).isEqualTo("INPATIENT");

        clock.advance(Duration.ofHours(30));   // 2 bed-days in A
        desk.post("/api/v1/admissions/" + adm + "/transfer", Map.of("bedId", beds.get(1)));
        JsonNode board = desk.get("/api/v1/wards").body();
        JsonNode ward = null;
        for (JsonNode w : board) if (w.get("id").asLong() == wardId) ward = w;
        assertThat(ward.at("/rooms/0/beds/0/status").asText()).as("old bed goes to cleaning").isEqualTo("CLEANING");
        assertThat(ward.at("/rooms/0/beds/1/status").asText()).isEqualTo("OCCUPIED");

        clock.advance(Duration.ofHours(10));   // 1 bed-day in B
        assertThat(desk.post("/api/v1/admissions/" + adm + "/discharge", Map.of("dischargeType", "ROUTINE", "summary", "short")).status()).isEqualTo(400);
        JsonNode discharged = desk.post("/api/v1/admissions/" + adm + "/discharge",
                Map.of("dischargeType", "ROUTINE", "summary", "Improved on IV antibiotics; oral course to complete at home.")).body();
        assertThat(discharged.get("status").asText()).isEqualTo("DISCHARGED");
        assertThat(discharged.get("stays")).hasSize(2);

        JsonNode charges = desk.get("/api/v1/patients/" + patient + "/unbilled-charges").body();
        assertThat(charges.findValuesAsText("category")).containsOnly("ADMISSION");
        assertThat(charges.findValues("amount").stream().map(JsonNode::decimalValue).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add))
                .isEqualByComparingTo("15000.00");   // (2 + 1) days x 5000
        assertThat(doctor.get("/api/v1/patients/" + patient + "/emr").body().at("/encounters/0/status").asText()).isEqualTo("CLOSED");
    }

    @Test
    void anOccupiedOrUnavailableBedCannotBeAllocated() throws Exception {
        long a1 = requestAdmission(Fixtures.patient(desk));
        long a2 = requestAdmission(Fixtures.patient(desk));
        desk.post("/api/v1/admissions/" + a1 + "/admit", Map.of("bedId", beds.get(0)));
        assertThat(desk.post("/api/v1/admissions/" + a2 + "/admit", Map.of("bedId", beds.get(0))).code()).isEqualTo("BED_NOT_AVAILABLE");
        assertThat(admin.post("/api/v1/beds/" + beds.get(0) + "/status", Map.of("status", "AVAILABLE")).code()).isEqualTo("BED_OCCUPIED");
        admin.post("/api/v1/beds/" + beds.get(1) + "/status", Map.of("status", "MAINTENANCE"));
        assertThat(desk.post("/api/v1/admissions/" + a2 + "/admit", Map.of("bedId", beds.get(1))).code()).isEqualTo("BED_NOT_AVAILABLE");
        assertThat(admin.post("/api/v1/beds/" + beds.get(1) + "/status", Map.of("status", "OCCUPIED")).status()).isEqualTo(400);
    }

    @Test
    void concurrentAdmissionsToOneBedYieldOneSuccess() throws Exception {
        List<Long> requests = new ArrayList<>();
        for (int i = 0; i < 4; i++) requests.add(requestAdmission(Fixtures.patient(desk)));
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<Api.Response>> results = new ArrayList<>();
        for (long id : requests) {
            results.add(pool.submit(() -> {
                go.await();
                return desk.post("/api/v1/admissions/" + id + "/admit", Map.of("bedId", beds.get(0)));
            }));
        }
        go.countDown();
        int ok = 0;
        for (Future<Api.Response> f : results) if (f.get().status() == 200) ok++;
        pool.shutdown();
        assertThat(ok).isEqualTo(1);
    }

    @Test
    void permissions() throws Exception {
        long adm = requestAdmission(Fixtures.patient(desk));
        assertThat(doctor.post("/api/v1/admissions/" + adm + "/admit", Map.of("bedId", beds.get(0))).status()).as("doctor requests, desk admits").isEqualTo(403);
        assertThat(api.as("NURSE").get("/api/v1/wards").status()).isEqualTo(200);
        assertThat(api.as("PHARMACIST").get("/api/v1/admissions").status()).isEqualTo(403);
        assertThat(desk.post("/api/v1/admissions/" + adm + "/cancel", Map.of("reason", "Patient declined")).body().get("status").asText()).isEqualTo("CANCELLED");
    }
}
