package lk.hms.modules.auth;

import lk.hms.modules.users.entity.User;
import lk.hms.support.TestDatabaseConfig;
import lk.hms.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real HTTP round-trips against an embedded server, so cookie attributes are exactly what a
 * browser receives (MockMvc's security test support replaces the CSRF repository).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestDatabaseConfig.class)
class AuthCookiesHttpTest {

    @LocalServerPort int port;
    @Autowired TestUsers testUsers;

    private final HttpClient http = HttpClient.newHttpClient();

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    private static String cookie(HttpResponse<?> res, String name) {
        return res.headers().allValues("Set-Cookie").stream()
                .filter(h -> h.startsWith(name + "=")).findFirst().orElse(null);
    }

    private static String value(String setCookie) {
        return setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
    }

    @Test
    void csrfAndSessionCookiesHaveSecureAttributes() throws Exception {
        HttpResponse<Void> csrf = http.send(HttpRequest.newBuilder(URI.create(url("/api/v1/auth/csrf"))).GET().build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(csrf.statusCode()).isEqualTo(204);
        String xsrf = cookie(csrf, "XSRF-TOKEN");
        assertThat(xsrf).as("XSRF cookie issued").isNotNull()
                .contains("SameSite=Strict")
                .doesNotContainIgnoringCase("HttpOnly");          // the SPA must read it
        assertThat(cookie(csrf, "HMS_SESSION")).as("no session before login").isNull();

        User u = testUsers.active("RECEPTIONIST");
        String token = value(xsrf);
        HttpResponse<String> login = http.send(HttpRequest.newBuilder(URI.create(url("/api/v1/auth/login")))
                        .header("Content-Type", "application/json")
                        .header("X-XSRF-TOKEN", token)
                        .header("Cookie", "XSRF-TOKEN=" + token)
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"username\":\"" + u.getUsername() + "\",\"password\":\"" + TestUsers.PASSWORD + "\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(login.statusCode()).isEqualTo(200);
        String session = cookie(login, "HMS_SESSION");
        assertThat(session).isNotNull().contains("HttpOnly").contains("SameSite=Strict");
        String rotatedXsrf = cookie(login, "XSRF-TOKEN");
        assertThat(rotatedXsrf).as("CSRF token rotated at login").isNotNull();
        assertThat(value(rotatedXsrf)).isNotEqualTo(token);

        List<String> securityHeaders = List.of("X-Content-Type-Options", "X-Frame-Options", "X-Request-Id");
        securityHeaders.forEach(h -> assertThat(login.headers().firstValue(h)).as(h).isPresent());
    }
}
