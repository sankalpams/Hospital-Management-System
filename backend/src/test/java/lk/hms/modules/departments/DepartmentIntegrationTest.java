package lk.hms.modules.departments;

import lk.hms.support.Api;
import lk.hms.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class DepartmentIntegrationTest {

    @Autowired Api api;
    @Autowired JdbcTemplate jdbc;

    static Map<String, Object> dept(String code, String name) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", code);
        m.put("name", name);
        m.put("type", "CLINICAL");
        m.put("location", "Block A");
        return m;
    }

    private static String code() {
        return "D" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    @Test
    void adminManagesDepartmentsAndCodesAreUniqueAndImmutable() throws Exception {
        Api.Session admin = api.as("HOSPITAL_ADMIN");
        String code = code();
        Api.Response created = admin.post("/api/v1/departments", dept(code.toLowerCase(), "Cardiology " + code));
        assertThat(created.status()).as(created.toString()).isEqualTo(201);
        assertThat(created.body().get("code").asText()).isEqualTo(code);
        long id = created.body().get("id").asLong();

        assertThat(admin.post("/api/v1/departments", dept(code, "Other " + code)).code()).isEqualTo("DEPARTMENT_CODE_TAKEN");
        assertThat(admin.post("/api/v1/departments", dept(code(), "cardiology " + code)).code()).isEqualTo("DEPARTMENT_NAME_TAKEN");

        Map<String, Object> rename = dept(code, "Cardiology Unit " + code);
        assertThat(admin.put("/api/v1/departments/" + id, rename).body().get("name").asText()).startsWith("Cardiology Unit");
        assertThat(admin.put("/api/v1/departments/" + id, dept(code(), "X " + code)).code()).isEqualTo("CODE_IMMUTABLE");
    }

    @Test
    void departmentWithActiveStaffCannotBeDeactivated() throws Exception {
        Api.Session admin = api.as("HOSPITAL_ADMIN");
        String code = code();
        long id = admin.post("/api/v1/departments", dept(code, "Staffed " + code)).body().get("id").asLong();
        jdbc.update("""
                INSERT INTO employees(employee_no, department_id, first_name, last_name, gender, phone, designation,
                                      staff_category, employment_type, join_date)
                VALUES (?, ?, 'A', 'B', 'FEMALE', '0771234567', 'Nurse', 'NURSE', 'PERMANENT', '2024-01-01')
                """, "EMP-" + code, id);
        Api.Response res = admin.post("/api/v1/departments/" + id + "/deactivate", null);
        assertThat(res.code()).isEqualTo("DEPARTMENT_IN_USE");
        assertThat(admin.get("/api/v1/departments/" + id).body().get("activeStaff").asInt()).isEqualTo(1);
    }

    @Test
    void readersCannotManage() throws Exception {
        Api.Session nurse = api.as("NURSE");
        assertThat(nurse.get("/api/v1/departments/options").status()).isEqualTo(200);
        assertThat(nurse.post("/api/v1/departments", dept(code(), "Nope")).status()).isEqualTo(403);
    }
}
