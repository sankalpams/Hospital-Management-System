package lk.hms.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import lk.hms.modules.users.entity.User;
import lk.hms.support.Api;
import lk.hms.support.Fixtures;
import lk.hms.support.IntegrationTest;
import lk.hms.support.MutableClock;
import lk.hms.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Security behaviour not covered by the module tests (docs/07 security checklist). */
@IntegrationTest
class SecurityHardeningIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TestUsers testUsers;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired Api api;

    private MvcResult login(String username, String password, Cookie... cookies) throws Exception {
        var req = post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", password)));
        if (cookies.length > 0) req.cookie(cookies);
        return mvc.perform(req).andReturn();
    }

    private Cookie session(User u) throws Exception {
        return login(u.getUsername(), TestUsers.PASSWORD).getResponse().getCookie("HMS_SESSION");
    }

    @Test
    void authenticatedWritesNeedTheCsrfToken() throws Exception {
        Cookie s = session(testUsers.active("RECEPTIONIST"));
        String body = "{\"name\":\"x\"}";
        int none = mvc.perform(post("/api/v1/notifications/read-all").cookie(s)).andReturn().getResponse().getStatus();
        int forged = mvc.perform(post("/api/v1/notifications/read-all").cookie(s, new Cookie("XSRF-TOKEN", "abc"))
                .header("X-XSRF-TOKEN", "def").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
        int ok = mvc.perform(post("/api/v1/notifications/read-all").cookie(s).with(csrf())).andReturn().getResponse().getStatus();
        assertThat(none).isEqualTo(403);
        assertThat(forged).as("header must match the cookie").isEqualTo(403);
        assertThat(ok).isIn(200, 204);
    }

    @Test
    void loginIssuesAFreshSessionId() throws Exception {
        User u = testUsers.active("NURSE");
        Cookie before = session(u);
        MvcResult again = login(u.getUsername(), TestUsers.PASSWORD, before);
        Cookie after = again.getResponse().getCookie("HMS_SESSION");
        assertThat(after).isNotNull();
        assertThat(after.getValue()).as("session fixation: the old id must not carry over").isNotEqualTo(before.getValue());
    }

    @Test
    void idleSessionsExpireAfterThirtyMinutes() throws Exception {
        Cookie s = session(testUsers.active("NURSE"));
        String id = new String(Base64.getDecoder().decode(s.getValue()));
        Integer idle = jdbc.queryForObject("SELECT MAX_INACTIVE_INTERVAL FROM SPRING_SESSION WHERE SESSION_ID = ?", Integer.class, id);
        assertThat(idle).isEqualTo(1800);
        assertThat(mvc.perform(get("/api/v1/auth/me").cookie(s)).andReturn().getResponse().getStatus()).isEqualTo(200);
        long past = System.currentTimeMillis() - Duration.ofMinutes(31).toMillis();
        jdbc.update("UPDATE SPRING_SESSION SET LAST_ACCESS_TIME = ?, EXPIRY_TIME = ? WHERE SESSION_ID = ?", past, past + 1_800_000L, id);
        assertThat(mvc.perform(get("/api/v1/auth/me").cookie(s)).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void lockoutLiftsAfterTheLockPeriod() throws Exception {
        clock.reset();
        User u = testUsers.active("PHARMACIST");
        for (int i = 0; i < 5; i++) login(u.getUsername(), "Wrong-" + i);
        assertThat(login(u.getUsername(), TestUsers.PASSWORD).getResponse().getStatus()).isEqualTo(423);
        clock.advance(Duration.ofMinutes(16));
        assertThat(login(u.getUsername(), TestUsers.PASSWORD).getResponse().getStatus()).isEqualTo(200);
        clock.reset();
    }

    private int loginFrom(String ip, String username, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").with(csrf()).with(r -> { r.setRemoteAddr(ip); return r; })
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", password)))).andReturn().getResponse().getStatus();
    }

    @Test
    void oneAddressTryingManyAccountsIsPaused() throws Exception {
        clock.reset();
        jdbc.update("UPDATE system_settings SET setting_value = '30' WHERE setting_key = 'security.ip_max_failed_logins'");
        try {
            User real = testUsers.active("NURSE");
            for (int i = 0; i < 30; i++) {
                assertThat(loginFrom("203.0.113.7", "nobody" + i, "Wrong-" + i)).isEqualTo(401);
            }
            assertThat(loginFrom("203.0.113.7", real.getUsername(), TestUsers.PASSWORD)).as("even the right password").isEqualTo(429);
            assertThat(loginFrom("203.0.113.8", real.getUsername(), TestUsers.PASSWORD)).as("other computers are unaffected").isEqualTo(200);
            clock.advance(Duration.ofMinutes(16));
            assertThat(loginFrom("203.0.113.7", real.getUsername(), TestUsers.PASSWORD)).as("pause lifts after the window").isEqualTo(200);
        } finally {
            jdbc.update("UPDATE system_settings SET setting_value = '1000000' WHERE setting_key = 'security.ip_max_failed_logins'");
            clock.reset();
        }
    }

    @Test
    void apiResponsesCarryProtectiveHeadersAndAreNotCached() throws Exception {
        MockHttpServletResponse r = mvc.perform(get("/api/v1/auth/me").cookie(session(testUsers.active("NURSE")))).andReturn().getResponse();
        assertThat(r.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(r.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(r.getHeader("Referrer-Policy")).isEqualTo("same-origin");
        assertThat(r.getHeader("Cache-Control")).contains("no-store");
    }

    @Test
    void foreignOriginsGetNoCorsGrant() throws Exception {
        MockHttpServletResponse r = mvc.perform(options("/api/v1/patients").header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "GET")).andReturn().getResponse();
        assertThat(r.getHeader("Access-Control-Allow-Origin")).isNull();
        assertThat(r.getStatus()).isEqualTo(403);
    }

    @Test
    void searchInputIsDataNotSql() throws Exception {
        Api.Session desk = api.as("RECEPTIONIST");
        Fixtures.patient(desk);
        for (String probe : new String[]{"' OR '1'='1", "x%' UNION SELECT password_hash FROM users -- ", "\\", "%_%"}) {
            Api.Response r = desk.get("/api/v1/patients?q=" + java.net.URLEncoder.encode(probe, java.nio.charset.StandardCharsets.UTF_8));
            assertThat(r.status()).as(probe).isEqualTo(200);
            assertThat(r.body().get("totalElements").asLong()).as("no row matches " + probe).isZero();
        }
    }

    @Test
    void errorsDoNotLeakInternals() throws Exception {
        Api.Session admin = api.as("HOSPITAL_ADMIN");
        for (Api.Response r : new Api.Response[]{admin.get("/api/v1/patients/999999"), admin.get("/api/v1/patients/not-a-number"),
                admin.post("/api/v1/departments", Map.of("code", "!!"))}) {
            String text = r.raw().getResponse().getContentAsString();
            assertThat(r.status()).isBetween(400, 499);
            assertThat(text).doesNotContain("Exception", "at lk.hms", "SQL", "org.springframework").contains("\"code\"");
        }
    }
}
