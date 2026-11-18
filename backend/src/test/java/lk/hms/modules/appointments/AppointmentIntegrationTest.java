package lk.hms.modules.appointments;

import lk.hms.support.Api;
import lk.hms.support.Fixtures;
import lk.hms.support.IntegrationTest;
import lk.hms.support.MutableClock;
import lk.hms.support.TestUsers;
import org.junit.jupiter.api.AfterEach;
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

import static lk.hms.support.Fixtures.book;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Clock: Monday 2030-06-03 08:30 Asia/Colombo. "Today" = 2030-06-03 (Mon), next week = 2030-06-10.
 * Each test builds its own department, doctor and Monday session 09:00-12:00 with 15-min slots
 * capped at 4 patients (09:00, 09:15, 09:30, 09:45).
 */
@IntegrationTest
class AppointmentIntegrationTest {

    static final String TODAY = "2030-06-03";
    static final String NEXT_MONDAY = "2030-06-10";

    @Autowired Api api;
    @Autowired TestUsers testUsers;
    @Autowired MutableClock clock;

    Api.Session admin;
    Api.Session desk;
    Api.Session doctorUser;
    long doctorId;

    @BeforeEach
    void setUp() throws Exception {
        clock.reset();
        admin = api.as("HOSPITAL_ADMIN");
        desk = api.as("RECEPTIONIST");
        doctorUser = api.as("DOCTOR");
        long dept = Fixtures.department(admin);
        doctorId = Fixtures.doctor(admin, dept, doctorUser.user.getId());
        Fixtures.schedule(admin, doctorId, 1, "09:00", "12:00", 15, 4);
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    void availabilityListsOnlyScheduledSlotsUpToCapacity() throws Exception {
        Api.Response a = desk.get("/api/v1/doctors/" + doctorId + "/availability?date=" + TODAY);
        assertThat(a.body().get("sessions")).hasSize(1);
        assertThat(a.body().at("/sessions/0/slots").findValuesAsText("time")).containsExactly("09:00:00", "09:15:00", "09:30:00", "09:45:00");
        assertThat(desk.get("/api/v1/doctors/" + doctorId + "/availability?date=2030-06-04").body().get("blockedReason").asText())
                .contains("No consultation session");
    }

    @Test
    void bookingRulesPreventDoubleBookingAndOffScheduleTimes() throws Exception {
        long p1 = Fixtures.patient(desk);
        long p2 = Fixtures.patient(desk);

        Api.Response first = book(desk, p1, doctorId, TODAY, "09:00");
        assertThat(first.status()).as(first.toString()).isEqualTo(201);
        assertThat(first.body().get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(first.body().get("tokenNo").asInt()).isEqualTo(1);
        assertThat(first.body().get("slotStart").asText()).isEqualTo("2030-06-03T03:30:00Z");   // 09:00 Colombo = 03:30 UTC
        assertThat(first.body().get("appointmentNo").asText()).matches("APT-2030-\\d{6}");

        assertThat(book(desk, p2, doctorId, TODAY, "09:00").code()).isEqualTo("SLOT_TAKEN");
        assertThat(book(desk, p2, doctorId, TODAY, "10:00").code()).as("beyond capacity").isEqualTo("SLOT_NOT_IN_SCHEDULE");
        assertThat(book(desk, p2, doctorId, TODAY, "09:07").code()).as("not aligned").isEqualTo("SLOT_NOT_IN_SCHEDULE");
        assertThat(book(desk, p2, doctorId, "2030-06-04", "09:00").code()).as("no Tuesday session").isEqualTo("SLOT_NOT_IN_SCHEDULE");
        assertThat(book(desk, p2, doctorId, "2030-05-27", "09:00").code()).isEqualTo("DATE_IN_PAST");
        assertThat(book(desk, p2, doctorId, TODAY, "09:15").body().get("tokenNo").asInt()).isEqualTo(2);

        // Same patient, another doctor, same time
        long otherDoctor = Fixtures.doctor(admin, Fixtures.department(admin), null);
        Fixtures.schedule(admin, otherDoctor, 1, "09:00", "10:00", 15, 4);
        assertThat(book(desk, p1, otherDoctor, TODAY, "09:00").code()).isEqualTo("PATIENT_DOUBLE_BOOKED");
    }

    @Test
    void unavailabilityBlocksBookings() throws Exception {
        long p = Fixtures.patient(desk);
        Api.Response x = admin.post("/api/v1/doctors/" + doctorId + "/exceptions",
                Map.of("dateFrom", NEXT_MONDAY, "dateTo", NEXT_MONDAY, "reason", "Conference"));
        assertThat(x.status()).isEqualTo(201);
        assertThat(desk.get("/api/v1/doctors/" + doctorId + "/availability?date=" + NEXT_MONDAY).body().get("blockedReason").asText())
                .contains("Conference");
        assertThat(book(desk, p, doctorId, NEXT_MONDAY, "09:00").code()).isEqualTo("DOCTOR_UNAVAILABLE");
    }

    @Test
    void fullConsultationLifecycleFollowsTheStateMachine() throws Exception {
        long p = Fixtures.patient(desk);
        long id = book(desk, p, doctorId, TODAY, "09:00").body().get("id").asLong();
        String base = "/api/v1/appointments/" + id;

        assertThat(desk.post(base + "/check-in", null).body().get("status").asText()).isEqualTo("CHECKED_IN");
        assertThat(desk.post(base + "/start", null).status()).as("receptionist cannot consult").isEqualTo(403);

        Api.Session otherDoctor = api.as("DOCTOR");
        assertThat(otherDoctor.post(base + "/start", null).code()).isEqualTo("NOT_YOUR_APPOINTMENT");

        assertThat(doctorUser.post(base + "/start", null).body().get("status").asText()).isEqualTo("IN_PROGRESS");
        long encounterId = doctorUser.get("/api/v1/patients/" + p + "/emr").body().at("/encounters/0/id").asLong();
        doctorUser.post("/api/v1/encounters/" + encounterId + "/diagnoses",
                Map.of("description", "Viral fever", "type", "PRIMARY", "certainty", "PROVISIONAL"));
        assertThat(doctorUser.post(base + "/complete", null).body().get("status").asText()).isEqualTo("COMPLETED");
        assertThat(desk.post(base + "/cancel", Map.of("reason", "Too late")).code()).isEqualTo("INVALID_STATUS_TRANSITION");

        Api.Response mine = doctorUser.get("/api/v1/appointments?mine=true&from=" + TODAY + "&to=" + TODAY);
        assertThat(mine.body().get("content").findValuesAsText("id")).contains(String.valueOf(id));
    }

    @Test
    void checkInOnlyOnTheDayAndNoShowOnlyAfterTheSlot() throws Exception {
        long p = Fixtures.patient(desk);
        long future = book(desk, p, doctorId, NEXT_MONDAY, "09:00").body().get("id").asLong();
        assertThat(desk.post("/api/v1/appointments/" + future + "/check-in", null).code()).isEqualTo("CHECK_IN_NOT_TODAY");

        long todayId = book(desk, Fixtures.patient(desk), doctorId, TODAY, "09:30").body().get("id").asLong();
        assertThat(desk.post("/api/v1/appointments/" + todayId + "/no-show", null).code()).isEqualTo("TOO_EARLY_FOR_NO_SHOW");
        clock.advance(Duration.ofHours(1));   // 09:30 local
        assertThat(desk.post("/api/v1/appointments/" + todayId + "/no-show", null).body().get("status").asText()).isEqualTo("NO_SHOW");
    }

    @Test
    void cancellationNeedsReasonAndFreesTheSlot() throws Exception {
        long p = Fixtures.patient(desk);
        long id = book(desk, p, doctorId, NEXT_MONDAY, "09:45").body().get("id").asLong();
        assertThat(desk.post("/api/v1/appointments/" + id + "/cancel", Map.of("reason", "")).status()).isEqualTo(400);
        Api.Response cancelled = desk.post("/api/v1/appointments/" + id + "/cancel", Map.of("reason", "Patient travelling"));
        assertThat(cancelled.body().get("status").asText()).isEqualTo("CANCELLED");
        assertThat(book(desk, Fixtures.patient(desk), doctorId, NEXT_MONDAY, "09:45").status()).isEqualTo(201);
    }

    @Test
    void rescheduleCancelsTheOldBookingAndLinksTheNewOne() throws Exception {
        long p = Fixtures.patient(desk);
        Api.Response original = book(desk, p, doctorId, TODAY, "09:15");
        long oldId = original.body().get("id").asLong();
        Api.Response moved = desk.post("/api/v1/appointments/" + oldId + "/reschedule",
                Map.of("doctorId", doctorId, "date", NEXT_MONDAY, "time", "09:15"));
        assertThat(moved.status()).as(moved.toString()).isEqualTo(200);
        assertThat(moved.body().get("rescheduledFromId").asLong()).isEqualTo(oldId);
        assertThat(moved.body().get("status").asText()).isEqualTo("CONFIRMED");

        Api.Response old = desk.get("/api/v1/appointments/" + oldId);
        assertThat(old.body().get("status").asText()).isEqualTo("CANCELLED");
        assertThat(old.body().get("cancelReason").asText()).isEqualTo("Rescheduled to " + moved.body().get("appointmentNo").asText());
    }

    @Test
    void walkInTakesTheNextFreeSlotAndIsCheckedIn() throws Exception {
        clock.advance(Duration.ofMinutes(50));   // 09:20 local: 09:00 slot is over, 09:15 still running
        book(desk, Fixtures.patient(desk), doctorId, TODAY, "09:30");
        Api.Response w = desk.post("/api/v1/appointments/walk-in", Map.of("patientId", Fixtures.patient(desk), "doctorId", doctorId));
        assertThat(w.status()).as(w.toString()).isEqualTo(201);
        assertThat(w.body().get("localTime").asText()).isEqualTo("09:15:00");
        assertThat(w.body().get("status").asText()).isEqualTo("CHECKED_IN");
        assertThat(w.body().get("type").asText()).isEqualTo("WALK_IN");
    }

    @Test
    void concurrentBookingsForTheSameSlotYieldExactlyOneSuccess() throws Exception {
        int attempts = 6;
        List<Long> patients = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            patients.add(Fixtures.patient(desk));
        }
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        List<Future<Api.Response>> results = new ArrayList<>();
        for (long patient : patients) {
            results.add(pool.submit(() -> {
                go.await();
                return book(desk, patient, doctorId, NEXT_MONDAY, "09:30");
            }));
        }
        go.countDown();
        int created = 0;
        int conflicts = 0;
        for (Future<Api.Response> f : results) {
            Api.Response r = f.get();
            if (r.status() == 201) created++;
            else if (r.status() == 409) conflicts++;
        }
        pool.shutdown();
        assertThat(created).isEqualTo(1);
        assertThat(conflicts).isEqualTo(attempts - 1);
    }

    @Test
    void calendarCountsPerLocalDay() throws Exception {
        book(desk, Fixtures.patient(desk), doctorId, TODAY, "09:00");
        book(desk, Fixtures.patient(desk), doctorId, TODAY, "09:15");
        Api.Response cal = desk.get("/api/v1/appointments/calendar?from=" + TODAY + "&to=" + NEXT_MONDAY + "&doctorId=" + doctorId);
        assertThat(cal.body()).hasSize(1);
        assertThat(cal.body().get(0).get("date").asText()).isEqualTo(TODAY);
        assertThat(cal.body().get(0).get("total").asInt()).isEqualTo(2);
    }

    @Test
    void scheduleOverlapAndPermissionsAreEnforced() throws Exception {
        Api.Response overlap = admin.post("/api/v1/doctors/" + doctorId + "/schedules", Map.of(
                "dayOfWeek", 1, "startTime", "11:30", "endTime", "13:00", "slotMinutes", 15, "maxPatients", 6,
                "effectiveFrom", "2030-06-01"));
        assertThat(overlap.code()).isEqualTo("SCHEDULE_OVERLAP");
        assertThat(desk.post("/api/v1/doctors", Fixtures.doctorBody(1, null)).status()).isEqualTo(403);
        assertThat(api.as("PHARMACIST").get("/api/v1/appointments").status()).isEqualTo(403);
        // A well-formed request is still refused: permissions are enforced server-side, not just hidden in the UI.
        assertThat(book(api.as("NURSE"), Fixtures.patient(desk), doctorId, NEXT_MONDAY, "09:00").status()).isEqualTo(403);
    }

    @Test
    void doctorUserLinkRequiresDoctorRoleAndIsUnique() throws Exception {
        long dept = Fixtures.department(admin);
        Long nurseUserId = testUsers.active("NURSE").getId();
        assertThat(admin.post("/api/v1/doctors", Fixtures.doctorBody(dept, nurseUserId)).code()).isEqualTo("USER_NOT_DOCTOR");
        assertThat(admin.post("/api/v1/doctors", Fixtures.doctorBody(dept, doctorUser.user.getId())).code()).isEqualTo("USER_ALREADY_LINKED");
    }
}
