package lk.hms.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/** Builds departments, doctors, schedules and patients through the real API. */
public final class Fixtures {

    private Fixtures() {
    }

    public static String uid() {
        return UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    public static long department(Api.Session admin) throws Exception {
        String code = "T" + uid();
        Api.Response r = admin.post("/api/v1/departments", Map.of("code", code, "name", "Dept " + code, "type", "CLINICAL"));
        assertThat(r.status()).as(r.toString()).isEqualTo(201);
        return r.body().get("id").asLong();
    }

    public static Map<String, Object> doctorBody(long departmentId, Long userId) {
        Map<String, Object> m = new HashMap<>();
        m.put("firstName", "Anura");
        m.put("lastName", "Fernando" + uid());
        m.put("gender", "MALE");
        m.put("phone", "0771234567");
        m.put("departmentId", departmentId);
        m.put("employmentType", "PERMANENT");
        m.put("joinDate", "2020-01-01");
        m.put("registrationNo", "SLMC-" + uid());
        m.put("specialization", "General Medicine");
        m.put("consultationFee", new BigDecimal("2500.00"));
        m.put("acceptingPatients", true);
        if (userId != null) {
            m.put("userId", userId);
        }
        return m;
    }

    public static long doctor(Api.Session admin, long departmentId, Long userId) throws Exception {
        Api.Response r = admin.post("/api/v1/doctors", doctorBody(departmentId, userId));
        assertThat(r.status()).as(r.toString()).isEqualTo(201);
        return r.body().get("id").asLong();
    }

    /** Session on the given ISO weekday; 4 slots of 15 minutes by default from start. */
    public static long schedule(Api.Session admin, long doctorId, int dayOfWeek, String start, String end, int slot, int max)
            throws Exception {
        Api.Response r = admin.post("/api/v1/doctors/" + doctorId + "/schedules", Map.of(
                "dayOfWeek", dayOfWeek, "startTime", start, "endTime", end, "slotMinutes", slot, "maxPatients", max,
                "effectiveFrom", "2030-06-01"));
        assertThat(r.status()).as(r.toString()).isEqualTo(201);
        return r.body().get("id").asLong();
    }

    public static long patient(Api.Session desk) throws Exception {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Map<String, Object> m = new HashMap<>();
        m.put("firstName", "Pat" + rnd.nextInt(1_000_000));
        m.put("lastName", "Ient" + rnd.nextInt(1_000_000));
        m.put("gender", "MALE");
        m.put("dateOfBirth", LocalDate.of(1970 + rnd.nextInt(40), 1 + rnd.nextInt(12), 1 + rnd.nextInt(28)).toString());
        m.put("phone", "07" + (10_000_000 + rnd.nextInt(89_999_999)));
        m.put("emergencyContacts", List.of());
        Api.Response r = desk.post("/api/v1/patients", m);
        assertThat(r.status()).as(r.toString()).isEqualTo(201);
        return r.body().get("id").asLong();
    }

    public static Api.Response book(Api.Session desk, long patientId, long doctorId, String date, String time) throws Exception {
        return desk.post("/api/v1/appointments", Map.of("patientId", patientId, "doctorId", doctorId, "date", date,
                "time", time, "confirmNow", true));
    }
}
