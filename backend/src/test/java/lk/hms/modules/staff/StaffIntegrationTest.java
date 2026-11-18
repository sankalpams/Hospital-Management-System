package lk.hms.modules.staff;

import com.fasterxml.jackson.databind.JsonNode;
import lk.hms.support.Api;
import lk.hms.support.Fixtures;
import lk.hms.support.IntegrationTest;
import lk.hms.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/** Clock: Monday 2030-06-03 08:30 Colombo. */
@IntegrationTest
class StaffIntegrationTest {

    @Autowired Api api;
    @Autowired MutableClock clock;

    Api.Session hr;
    long dept;

    @BeforeEach
    void setUp() throws Exception {
        clock.reset();
        hr = api.as("HR_MANAGER");
        dept = Fixtures.department(api.as("HOSPITAL_ADMIN"));
    }

    private Map<String, Object> body(String category, Long userId) {
        Map<String, Object> b = new HashMap<>();
        b.put("firstName", "Nimali");
        b.put("lastName", "Silva " + Fixtures.uid());
        b.put("gender", "FEMALE");
        b.put("nic", "19" + (1000000000L + ThreadLocalRandom.current().nextLong(8999999999L)));
        b.put("phone", "0771234567");
        b.put("departmentId", dept);
        b.put("designation", "Nursing Officer");
        b.put("staffCategory", category);
        b.put("employmentType", "PERMANENT");
        b.put("joinDate", "2024-01-15");
        if (userId != null) b.put("userId", userId);
        return b;
    }

    private long employee(Long userId) throws Exception {
        Api.Response r = hr.post("/api/v1/employees", body("NURSE", userId));
        assertThat(r.status()).as(r.toString()).isEqualTo(201);
        return r.body().get("id").asLong();
    }

    @Test
    void employeesOfEveryNonDoctorCategory() throws Exception {
        Map<String, Object> b = body("NURSE", null);
        JsonNode e = hr.post("/api/v1/employees", b).body();
        assertThat(e.get("employeeNo").asText()).startsWith("EMP-");
        assertThat(hr.post("/api/v1/employees", body("DOCTOR", null)).code()).isEqualTo("USE_DOCTORS_PAGE");
        Map<String, Object> dup = body("SUPPORT", null);
        dup.put("nic", b.get("nic"));
        assertThat(hr.post("/api/v1/employees", dup).code()).isEqualTo("NIC_EXISTS");

        assertThat(hr.get("/api/v1/employees?category=NURSE&q=" + b.get("lastName").toString().split(" ")[1]).body().get("totalElements").asInt()).isEqualTo(1);
        b.put("status", "RESIGNED");
        b.put("version", e.get("version").asLong());
        JsonNode resigned = hr.put("/api/v1/employees/" + e.get("id").asLong(), b).body();
        assertThat(resigned.get("leaveDate").asText()).isEqualTo("2030-06-03");
        assertThat(api.as("NURSE").post("/api/v1/employees", body("NURSE", null)).status()).isEqualTo(403);
    }

    @Test
    void attendanceRegister() throws Exception {
        long id = employee(null);
        JsonNode register = hr.get("/api/v1/attendance?date=2030-06-03").body();
        assertThat(register.findValuesAsText("employeeId")).contains(String.valueOf(id));

        String url = "/api/v1/attendance?date=2030-06-03";
        assertThat(hr.put(url, Map.of("entries", List.of(Map.of("employeeId", id, "status", "PRESENT", "clockIn", "08:00", "clockOut", "07:00")))).code()).isEqualTo("INVALID_TIME");
        assertThat(hr.put(url, Map.of("entries", List.of(Map.of("employeeId", id, "status", "ABSENT", "clockIn", "08:00")))).code()).isEqualTo("TIMES_NOT_ALLOWED");
        assertThat(hr.put("/api/v1/attendance?date=2030-06-04", Map.of("entries", List.of(Map.of("employeeId", id, "status", "ABSENT")))).code()).isEqualTo("FUTURE_DATE");
        hr.put(url, Map.of("entries", List.of(Map.of("employeeId", id, "status", "PRESENT", "clockIn", "07:55"))));
        hr.put(url, Map.of("entries", List.of(Map.of("employeeId", id, "status", "PRESENT", "clockIn", "07:55", "clockOut", "16:05"))));

        JsonNode history = hr.get("/api/v1/employees/" + id + "/attendance?from=2030-06-01&to=2030-06-30").body();
        assertThat(history).hasSize(1);
        assertThat(history.get(0).get("clockIn").asText()).isEqualTo("07:55:00");
        assertThat(history.get(0).get("clockOut").asText()).isEqualTo("16:05:00");
    }

    @Test
    void leaveRequestBalanceAndApproval() throws Exception {
        Api.Session nurse = api.as("NURSE");
        long id = employee(nurse.user.getId());
        long casual = 2; // V9 seed order: ANNUAL, CASUAL, ...

        Api.Response applied = nurse.post("/api/v1/leave-requests", Map.of("leaveTypeId", casual, "startDate", "2030-06-10", "endDate", "2030-06-12", "reason", "Family wedding"));
        assertThat(applied.status()).as(applied.toString()).isEqualTo(201);
        assertThat(applied.body().get("days").decimalValue()).isEqualByComparingTo("3");
        long leaveId = applied.body().get("id").asLong();

        assertThat(nurse.post("/api/v1/leave-requests", Map.of("leaveTypeId", casual, "startDate", "2030-06-12", "endDate", "2030-06-12", "halfDay", true, "reason", "Overlap")).code()).isEqualTo("LEAVE_OVERLAP");
        assertThat(nurse.post("/api/v1/leave-requests", Map.of("leaveTypeId", casual, "startDate", "2030-07-01", "endDate", "2030-07-05", "reason", "Too many")).code()).isEqualTo("LEAVE_BALANCE");
        assertThat(nurse.post("/api/v1/leave-requests", Map.of("employeeId", employee(null), "leaveTypeId", casual, "startDate", "2030-07-01", "endDate", "2030-07-01", "reason", "Not mine")).status()).isEqualTo(403);
        assertThat(nurse.post("/api/v1/leave-requests/" + leaveId + "/approve", Map.of()).status()).isEqualTo(403);

        assertThat(hr.post("/api/v1/leave-requests/" + leaveId + "/reject", Map.of()).status()).isEqualTo(400);
        assertThat(hr.post("/api/v1/leave-requests/" + leaveId + "/approve", Map.of("note", "Enjoy")).body().get("status").asText()).isEqualTo("APPROVED");
        assertThat(hr.post("/api/v1/leave-requests/" + leaveId + "/approve", Map.of()).code()).isEqualTo("INVALID_STATUS_TRANSITION");

        JsonNode mine = nurse.get("/api/v1/me/leave").body();
        JsonNode casualBalance = null;
        for (JsonNode b : mine.get("balances")) if (b.get("leaveTypeId").asLong() == casual) casualBalance = b;
        assertThat(casualBalance.get("approved").decimalValue()).isEqualByComparingTo("3");
        assertThat(casualBalance.get("remaining").decimalValue()).isEqualByComparingTo("4");

        JsonNode register = hr.get("/api/v1/attendance?date=2030-06-11").body();
        for (JsonNode row : register) {
            if (row.get("employeeId").asLong() == id) assertThat(row.get("onLeave").asText()).isEqualTo("Casual leave");
        }
        assertThat(nurse.post("/api/v1/leave-requests/" + leaveId + "/cancel", Map.of()).body().get("status").asText()).isEqualTo("CANCELLED");
    }

    @Test
    void nobodyDecidesTheirOwnLeave() throws Exception {
        employee(hr.user.getId());
        long leaveId = hr.post("/api/v1/leave-requests", Map.of("leaveTypeId", 1, "startDate", "2030-08-01", "endDate", "2030-08-02", "reason", "Holiday")).body().get("id").asLong();
        assertThat(hr.post("/api/v1/leave-requests/" + leaveId + "/approve", Map.of()).code()).isEqualTo("SELF_APPROVAL");
        assertThat(api.as("RECEPTIONIST").get("/api/v1/me/leave").code()).isEqualTo("NO_EMPLOYEE_RECORD");
    }
}
