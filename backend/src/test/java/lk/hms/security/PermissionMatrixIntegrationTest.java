package lk.hms.security;

import lk.hms.support.Api;
import lk.hms.support.IntegrationTest;
import lk.hms.support.MutableClock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.MethodParameter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Role × endpoint matrix, generated from the code and the database rather than written by hand:
 * <ol>
 *   <li>every /api/v1 endpoint has a {@code @PreAuthorize}, or is on the short list below of endpoints any
 *       signed-in user may call (they check ownership themselves);</li>
 *   <li>every permission code named in a guard exists in the permissions table (a typo would lock everyone out);</li>
 *   <li>over HTTP, every guarded GET endpoint answers 403 to each role without the permission and something
 *       other than 403 to each role with it, using the role grants seeded by Flyway (V9).</li>
 * </ol>
 * Write endpoints are covered by (1) and (2); over HTTP their body validation runs before method security
 * (docs/07, ordering note), so a generic empty body cannot prove a 403.
 */
@IntegrationTest
class PermissionMatrixIntegrationTest {

    /** Signed-in users only; each checks record ownership or permissions in its service. */
    private static final Set<String> ANY_SIGNED_IN = Set.of(
            "GET /api/v1/auth/me", "POST /api/v1/auth/logout", "POST /api/v1/auth/change-password",
            "GET /api/v1/notifications", "GET /api/v1/notifications/unread-count", "POST /api/v1/notifications/{id}/read",
            "POST /api/v1/notifications/read-all",
            "GET /api/v1/leave-types", "GET /api/v1/me/leave", "POST /api/v1/leave-requests", "POST /api/v1/leave-requests/{id}/cancel",
            "GET /api/v1/analytics/dashboard",
            // report permission is checked in the controller per report (see AnalyticsControllerPermissions below)
            "GET /api/v1/analytics/{report}", "GET /api/v1/analytics/doctors/workload", "GET /api/v1/analytics/{report}/export.csv");
    private static final Set<String> PUBLIC = Set.of("GET /api/v1/auth/csrf", "POST /api/v1/auth/login", "POST /api/v1/auth/sign-up");
    private static final Pattern CODE = Pattern.compile("'([a-z:-]+)'");

    @Autowired Api api;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;

    record Endpoint(String method, String path, HandlerMethod handler, Set<String> anyOf) {
        String key() {
            return method + " " + path;
        }
    }

    private List<Endpoint> endpoints() {
        List<Endpoint> out = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : mappings.getHandlerMethods().entrySet()) {
            HandlerMethod h = e.getValue();
            PreAuthorize pre = h.getMethodAnnotation(PreAuthorize.class);
            Set<String> codes = new TreeSet<>();
            if (pre != null) {
                Matcher m = CODE.matcher(pre.value());
                while (m.find()) codes.add(m.group(1));
            }
            for (String path : e.getKey().getPatternValues()) {
                if (!path.startsWith("/api/v1/")) continue;
                Set<RequestMethod> methods = e.getKey().getMethodsCondition().getMethods();
                for (RequestMethod rm : methods.isEmpty() ? Set.of(RequestMethod.GET) : methods) {
                    out.add(new Endpoint(rm.name(), path, h, pre == null ? null : codes));
                }
            }
        }
        out.sort(Comparator.comparing(Endpoint::key));
        return out;
    }

    @Test
    void everyEndpointIsGuardedOrDeliberatelyOpen() {
        List<String> unguarded = endpoints().stream()
                .filter(e -> e.anyOf() == null && !ANY_SIGNED_IN.contains(e.key()) && !PUBLIC.contains(e.key()))
                .map(Endpoint::key).toList();
        assertThat(unguarded).as("endpoints without @PreAuthorize that are not on an allow-list").isEmpty();
        List<String> guardedButListed = endpoints().stream().filter(e -> e.anyOf() != null && ANY_SIGNED_IN.contains(e.key())).map(Endpoint::key).toList();
        assertThat(guardedButListed).as("allow-list entries that are in fact guarded (stale list)").isEmpty();
    }

    @Test
    void everyPermissionNamedInAGuardExists() {
        Set<String> known = new HashSet<>(jdbc.queryForList("SELECT code FROM permissions", String.class));
        List<String> unknown = endpoints().stream().filter(e -> e.anyOf() != null)
                .flatMap(e -> e.anyOf().stream().filter(c -> !known.contains(c)).map(c -> e.key() + " -> " + c)).toList();
        assertThat(unknown).isEmpty();
        assertThat(endpoints().stream().filter(e -> e.anyOf() != null && e.anyOf().isEmpty()).map(Endpoint::key).toList())
                .as("guards whose expression names no permission").isEmpty();
    }

    @Test
    void guardedGetEndpointsAnswer403ExactlyToRolesWithoutThePermission() throws Exception {
        clock.reset();
        Map<String, Set<String>> grants = new TreeMap<>();
        jdbc.query("""
                SELECT r.code, p.code FROM roles r JOIN role_permissions rp ON rp.role_id = r.id JOIN permissions p ON p.id = rp.permission_id
                """, rs -> {
            grants.computeIfAbsent(rs.getString(1), k -> new HashSet<>()).add(rs.getString(2));
        });
        jdbc.queryForList("SELECT code FROM roles", String.class).forEach(r -> grants.putIfAbsent(r, Set.of()));
        Map<String, Api.Session> sessions = new HashMap<>();
        for (String role : grants.keySet()) sessions.put(role, api.as(role));

        List<String> wrong = new ArrayList<>();
        int checks = 0;
        for (Endpoint e : endpoints()) {
            if (!e.method().equals("GET") || e.anyOf() == null) continue;
            String url = url(e);
            for (Map.Entry<String, Set<String>> g : grants.entrySet()) {
                boolean allowed = e.anyOf().stream().anyMatch(g.getValue()::contains);
                int status = sessions.get(g.getKey()).get(url).status();
                checks++;
                if (allowed == (status == 403)) {
                    wrong.add(g.getKey() + " " + e.key() + " -> " + status + (allowed ? " (should be allowed)" : " (should be 403)"));
                }
            }
        }
        assertThat(wrong).as("role/endpoint pairs that disagree with the V9 grants").isEmpty();
        assertThat(checks).isGreaterThan(300);
    }

    @Test
    void reportPermissionsAreCheckedPerReport() throws Exception {
        Map<String, String> needs = Map.of("appointments", "report:operational", "revenue", "report:financial", "staff", "report:staff");
        Map<String, Set<String>> grants = new HashMap<>();
        jdbc.query("SELECT r.code, p.code FROM roles r JOIN role_permissions rp ON rp.role_id = r.id JOIN permissions p ON p.id = rp.permission_id",
                rs -> {
                    grants.computeIfAbsent(rs.getString(1), k -> new HashSet<>()).add(rs.getString(2));
                });
        List<String> wrong = new ArrayList<>();
        for (String role : jdbc.queryForList("SELECT code FROM roles", String.class)) {
            Api.Session s = api.as(role);
            for (Map.Entry<String, String> n : needs.entrySet()) {
                boolean allowed = grants.getOrDefault(role, Set.of()).contains(n.getValue());
                for (String path : List.of("/api/v1/analytics/" + n.getKey(), "/api/v1/analytics/" + n.getKey() + "/export.csv")) {
                    int status = s.get(path).status();
                    if (allowed != (status == 200)) wrong.add(role + " " + path + " -> " + status);
                }
            }
        }
        assertThat(wrong).isEmpty();
    }

    /** Path variables become a missing id; required query parameters get a value of the right type so binding never fails first. */
    private static String url(Endpoint e) {
        String path = e.path().replaceAll("\\{[^}]+}", "999999");
        StringJoiner q = new StringJoiner("&");
        for (MethodParameter p : e.handler().getMethodParameters()) {
            p.initParameterNameDiscovery(new org.springframework.core.DefaultParameterNameDiscoverer());
            RequestParam rp = p.getParameterAnnotation(RequestParam.class);
            if (rp == null || !rp.required() || !ValueConstants.DEFAULT_NONE.equals(rp.defaultValue())) continue;
            String name = rp.name().isEmpty() ? (rp.value().isEmpty() ? p.getParameterName() : rp.value()) : rp.name();
            Class<?> t = p.getParameterType();
            String v = t == LocalDate.class ? LocalDate.of(2030, 6, 3).toString()
                    : t == int.class || t == Integer.class || t == long.class || t == Long.class ? "2030" : "x";
            q.add(name + "=" + v);
        }
        return q.length() == 0 ? path : path + "?" + q;
    }
}
