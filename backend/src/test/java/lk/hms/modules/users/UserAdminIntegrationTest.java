package lk.hms.modules.users;

import lk.hms.modules.users.entity.User;
import lk.hms.modules.users.repository.UserRepository;
import lk.hms.support.Api;
import lk.hms.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class UserAdminIntegrationTest {

    @Autowired Api api;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired lk.hms.support.TestUsers testUsers;

    private static String uniqueName() {
        return "u" + UUID.randomUUID().toString().substring(0, 10);
    }

    private Api.Response createUser(Api.Session admin, String username, List<String> roles) throws Exception {
        return admin.post("/api/v1/users", Map.of("username", username, "fullName", "Nimal Perera", "roles", roles));
    }

    @Test
    void onlySuperAdminSeesLoginsOnTheSharedDemoPassword() throws Exception {
        String sharedHash = "$2a$12$" + UUID.randomUUID().toString().replace("-", "") + "shareddemohashxxxxxxxxxx";
        List<String> names = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            User u = testUsers.active("NURSE");
            jdbc.update("UPDATE users SET password_hash = ? WHERE id = ?", sharedHash, u.getId());
            names.add(u.getUsername());
        }
        Api.Response list = api.as("SUPER_ADMIN").get("/api/v1/users/demo-staff");
        assertThat(list.status()).isEqualTo(200);
        assertThat(list.body().findValuesAsText("username")).containsAll(names);
        assertThat(api.as("HOSPITAL_ADMIN").get("/api/v1/users/demo-staff").status()).isEqualTo(403);
    }

    @Test
    void rolesWithoutUserPermissionsAreForbidden() throws Exception {
        Api.Session receptionist = api.as("RECEPTIONIST");
        assertThat(receptionist.get("/api/v1/users").status()).isEqualTo(403);
        assertThat(createUser(receptionist, uniqueName(), List.of("NURSE")).status()).isEqualTo(403);
    }

    @Test
    void adminCreatesUserWithTemporaryPasswordThatMustBeChanged() throws Exception {
        Api.Session admin = api.as("HOSPITAL_ADMIN");
        String name = uniqueName();
        Api.Response created = createUser(admin, name.toUpperCase(), List.of("NURSE"));
        assertThat(created.status()).as(created.toString()).isEqualTo(201);
        assertThat(created.body().at("/user/username").asText()).isEqualTo(name);   // normalised
        String temp = created.body().get("temporaryPassword").asText();
        assertThat(temp).hasSize(14);

        User stored = users.findByUsername(name).orElseThrow();
        assertThat(stored.getPasswordHash()).doesNotContain(temp).startsWith("$2");
        Api.Session newUser = api.login(name, temp, stored);
        assertThat(newUser.get("/api/v1/auth/me").body().get("mustChangePassword").asBoolean()).isTrue();

        assertThat(createUser(admin, name, List.of("NURSE")).code()).isEqualTo("USERNAME_TAKEN");
        Integer audits = jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='CREATE' AND entity_type='USER' AND entity_id=?",
                Integer.class, String.valueOf(stored.getId()));
        assertThat(audits).isEqualTo(1);
    }

    @Test
    void validationErrorsAreReportedPerField() throws Exception {
        Api.Session admin = api.as("HOSPITAL_ADMIN");
        Api.Response res = admin.post("/api/v1/users", Map.of("username", "bad name!", "fullName", "", "roles", List.of()));
        assertThat(res.status()).isEqualTo(400);
        assertThat(res.body().get("fieldErrors").findValuesAsText("field")).contains("username", "fullName", "roles");
    }

    @Test
    void hospitalAdminCannotEscalateToSuperAdmin() throws Exception {
        Api.Session admin = api.as("HOSPITAL_ADMIN");
        Api.Response res = createUser(admin, uniqueName(), List.of("SUPER_ADMIN"));
        assertThat(res.status()).isEqualTo(403);
        assertThat(res.code()).isEqualTo("SUPER_ADMIN_REQUIRED");

        Api.Session superAdmin = api.as("SUPER_ADMIN");
        assertThat(admin.post("/api/v1/users/" + superAdmin.user.getId() + "/deactivate", null).code())
                .isEqualTo("SUPER_ADMIN_REQUIRED");
        assertThat(admin.post("/api/v1/users/" + superAdmin.user.getId() + "/reset-password", null).code())
                .isEqualTo("SUPER_ADMIN_REQUIRED");
    }

    @Test
    void deactivationEndsSessionsAndBlocksSignIn() throws Exception {
        Api.Session admin = api.as("HOSPITAL_ADMIN");
        Api.Session nurse = api.as("NURSE");
        assertThat(nurse.get("/api/v1/auth/me").status()).isEqualTo(200);

        Api.Response res = admin.post("/api/v1/users/" + nurse.user.getId() + "/deactivate", null);
        assertThat(res.status()).isEqualTo(200);
        assertThat(res.body().get("status").asText()).isEqualTo("INACTIVE");
        assertThat(nurse.get("/api/v1/auth/me").status()).as("session revoked").isEqualTo(401);

        assertThat(admin.post("/api/v1/users/" + nurse.user.getId() + "/activate", null).status()).isEqualTo(200);
    }

    @Test
    void cannotDeactivateOwnAccount() throws Exception {
        Api.Session admin = api.as("HOSPITAL_ADMIN");
        assertThat(admin.post("/api/v1/users/" + admin.user.getId() + "/deactivate", null).code())
                .isEqualTo("CANNOT_DEACTIVATE_SELF");
    }

    @Test
    void lastActiveSuperAdminCannotBeRemoved() throws Exception {
        Api.Session superAdmin = api.as("SUPER_ADMIN");
        Api.Session other = api.as("SUPER_ADMIN");
        // Make 'other' the only active Super Admin.
        jdbc.update("""
                UPDATE users SET status='INACTIVE' WHERE id <> ? AND id IN
                  (SELECT user_id FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE r.code='SUPER_ADMIN')
                """, other.user.getId());
        Api.Response res = other.put("/api/v1/users/" + other.user.getId(), Map.of(
                "fullName", "Only Admin", "roles", List.of("HOSPITAL_ADMIN"),
                "version", users.findById(other.user.getId()).orElseThrow().getVersion()));
        assertThat(res.code()).isEqualTo("LAST_SUPER_ADMIN");
        jdbc.update("UPDATE users SET status='ACTIVE' WHERE id = ?", superAdmin.user.getId());
    }

    @Test
    void roleChangeEndsTheUsersSessionsSoNewPermissionsApply() throws Exception {
        Api.Session admin = api.as("HOSPITAL_ADMIN");
        Api.Session target = api.as("NURSE");
        long version = users.findById(target.user.getId()).orElseThrow().getVersion();
        Api.Response res = admin.put("/api/v1/users/" + target.user.getId(),
                Map.of("fullName", "Promoted", "roles", List.of("RECEPTIONIST"), "version", version));
        assertThat(res.status()).as(res.toString()).isEqualTo(200);
        assertThat(target.get("/api/v1/auth/me").status()).isEqualTo(401);

        Api.Response stale = admin.put("/api/v1/users/" + target.user.getId(),
                Map.of("fullName", "Stale", "roles", List.of("RECEPTIONIST"), "version", version));
        assertThat(stale.code()).isEqualTo("CONCURRENT_MODIFICATION");
    }

    @Test
    void resetPasswordIssuesNewTemporaryPasswordAndEndsSessions() throws Exception {
        Api.Session admin = api.as("HOSPITAL_ADMIN");
        Api.Session target = api.as("PHARMACIST");
        Api.Response res = admin.post("/api/v1/users/" + target.user.getId() + "/reset-password", null);
        assertThat(res.status()).isEqualTo(200);
        assertThat(target.get("/api/v1/auth/me").status()).isEqualTo(401);
        Api.Session again = api.login(target.user.getUsername(), res.body().get("temporaryPassword").asText(), target.user);
        assertThat(again.get("/api/v1/auth/me").body().get("mustChangePassword").asBoolean()).isTrue();
    }

    @Test
    void searchFiltersByRoleAndText() throws Exception {
        Api.Session admin = api.as("HOSPITAL_ADMIN");
        String name = uniqueName();
        createUser(admin, name, List.of("LAB_STAFF"));
        Api.Response res = admin.get("/api/v1/users?q=" + name + "&role=LAB_STAFF");
        assertThat(res.body().get("totalElements").asLong()).isEqualTo(1);
        assertThat(admin.get("/api/v1/users?q=" + name + "&role=DOCTOR").body().get("totalElements").asLong()).isZero();
        assertThat(admin.get("/api/v1/users?size=1000").body().get("size").asInt()).isEqualTo(100);
    }

    @Test
    void auditLogIsReadableOnlyWithAuditPermission() throws Exception {
        assertThat(api.as("HOSPITAL_ADMIN").get("/api/v1/audit-logs").status()).isEqualTo(200);
        assertThat(api.as("HOSPITAL_ADMIN").get("/api/v1/login-activity?success=false").status()).isEqualTo(200);
        assertThat(api.as("ACCOUNTANT").get("/api/v1/audit-logs").status()).isEqualTo(403);
    }
}
