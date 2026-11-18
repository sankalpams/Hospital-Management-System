package lk.hms.modules.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import lk.hms.modules.users.entity.User;
import lk.hms.modules.users.entity.UserStatus;
import lk.hms.modules.users.repository.UserRepository;
import lk.hms.support.IntegrationTest;
import lk.hms.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class AuthIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TestUsers testUsers;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    private MvcResult login(String username, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", password))))
                .andReturn();
    }

    private Cookie sessionOf(MvcResult result) {
        Cookie c = result.getResponse().getCookie("HMS_SESSION");
        assertThat(c).as("session cookie").isNotNull();
        return c;
    }

    @Test
    void unauthenticatedRequestGets401Json() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void loginRequiresCsrfToken() throws Exception {
        User u = testUsers.active("RECEPTIONIST");
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", u.getUsername(), "password", TestUsers.PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void successfulLoginReturnsPermissionsAndEstablishesSession() throws Exception {
        User u = testUsers.active("RECEPTIONIST");
        MvcResult res = login(u.getUsername().toUpperCase(), TestUsers.PASSWORD);   // usernames are case-insensitive
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        assertThat(res.getResponse().getContentAsString())
                .contains("\"patient:create\"").doesNotContain("emr:read").doesNotContain("password");

        Cookie session = sessionOf(res);
        assertThat(session.isHttpOnly()).isTrue();
        mvc.perform(get("/api/v1/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(u.getUsername()))
                .andExpect(jsonPath("$.roles[0]").value("RECEPTIONIST"));

        Integer loginAudits = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action='LOGIN' AND actor_user_id=?", Integer.class, u.getId());
        Integer successAttempts = jdbc.queryForObject(
                "SELECT COUNT(*) FROM login_attempts WHERE success=TRUE AND user_id=?", Integer.class, u.getId());
        assertThat(loginAudits).isEqualTo(1);
        assertThat(successAttempts).isEqualTo(1);
    }

    @Test
    void wrongPasswordAndUnknownUserAreIndistinguishable() throws Exception {
        User u = testUsers.active("NURSE");
        MvcResult wrong = login(u.getUsername(), "Wrong-password-1");
        MvcResult unknown = login("no_such_user_xyz", "Wrong-password-1");
        assertThat(wrong.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknown.getResponse().getStatus()).isEqualTo(401);
        assertThat(json.readTree(wrong.getResponse().getContentAsString()).get("message").asText())
                .isEqualTo(json.readTree(unknown.getResponse().getContentAsString()).get("message").asText());
    }

    @Test
    void fiveFailuresLockTheAccountEvenForTheCorrectPassword() throws Exception {
        User u = testUsers.active("DOCTOR");
        for (int i = 0; i < 5; i++) {
            assertThat(login(u.getUsername(), "Wrong-password-" + i).getResponse().getStatus()).isEqualTo(401);
        }
        MvcResult locked = login(u.getUsername(), TestUsers.PASSWORD);
        assertThat(locked.getResponse().getStatus()).isEqualTo(423);
        assertThat(locked.getResponse().getContentAsString()).contains("ACCOUNT_LOCKED");
        assertThat(users.findById(u.getId()).orElseThrow().getLockedUntil()).isNotNull();
    }

    @Test
    void inactiveAccountIsOnlyRevealedAfterCorrectPassword() throws Exception {
        User u = testUsers.create("PHARMACIST", false, UserStatus.INACTIVE);
        assertThat(login(u.getUsername(), "Wrong-password-1").getResponse().getStatus()).isEqualTo(401);
        MvcResult res = login(u.getUsername(), TestUsers.PASSWORD);
        assertThat(res.getResponse().getStatus()).isEqualTo(403);
        assertThat(res.getResponse().getContentAsString()).contains("ACCOUNT_INACTIVE");
    }

    @Test
    void temporaryPasswordBlocksEverythingUntilChanged() throws Exception {
        User u = testUsers.create("ACCOUNTANT", true, UserStatus.ACTIVE);
        Cookie session = sessionOf(login(u.getUsername(), TestUsers.PASSWORD));

        mvc.perform(get("/api/v1/some/other/endpoint").cookie(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mvc.perform(get("/api/v1/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        // weak password rejected by policy
        mvc.perform(post("/api/v1/auth/change-password").with(csrf()).cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("currentPassword", TestUsers.PASSWORD, "newPassword", "abc"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));

        // same password rejected
        mvc.perform(post("/api/v1/auth/change-password").with(csrf()).cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("currentPassword", TestUsers.PASSWORD, "newPassword", TestUsers.PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_REUSED"));

        MvcResult changed = mvc.perform(post("/api/v1/auth/change-password").with(csrf()).cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("currentPassword", TestUsers.PASSWORD, "newPassword", "Brand-New-Pass-77"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false))
                .andReturn();

        Cookie rotated = sessionOf(changed);
        assertThat(rotated.getValue()).as("session id rotated").isNotEqualTo(session.getValue());
        mvc.perform(get("/api/v1/auth/me").cookie(rotated)).andExpect(status().isOk());
        assertThat(login(u.getUsername(), "Brand-New-Pass-77").getResponse().getStatus()).isEqualTo(200);
        assertThat(login(u.getUsername(), TestUsers.PASSWORD).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void passwordChangeSignsOutOtherSessionsButKeepsTheCurrentOne() throws Exception {
        User u = testUsers.active("HR_MANAGER");
        Cookie desk = sessionOf(login(u.getUsername(), TestUsers.PASSWORD));
        Cookie laptop = sessionOf(login(u.getUsername(), TestUsers.PASSWORD));

        MvcResult changed = mvc.perform(post("/api/v1/auth/change-password").with(csrf()).cookie(desk)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("currentPassword", TestUsers.PASSWORD, "newPassword", "Another-Pass-88"))))
                .andExpect(status().isOk())
                .andReturn();

        mvc.perform(get("/api/v1/auth/me").cookie(sessionOf(changed))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").cookie(laptop)).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutInvalidatesTheSession() throws Exception {
        User u = testUsers.active("LAB_STAFF");
        Cookie session = sessionOf(login(u.getUsername(), TestUsers.PASSWORD));
        mvc.perform(post("/api/v1/auth/logout").with(csrf()).cookie(session)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/me").cookie(session)).andExpect(status().isUnauthorized());
    }
}
