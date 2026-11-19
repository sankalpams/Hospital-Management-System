package lk.hms.demo;

import lk.hms.modules.auth.service.PasswordPolicy;
import lk.hms.modules.users.entity.Role;
import lk.hms.modules.users.entity.User;
import lk.hms.modules.users.entity.UserStatus;
import lk.hms.modules.users.repository.RoleRepository;
import lk.hms.modules.users.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Demo staff logins: gives about 40 existing employees (from the demo data) a sign-in account in the role that
 * matches their job, so the system can be tried as many different doctors, nurses and so on. Doctor accounts are
 * linked to their doctor profile and see their own appointments.
 * <p>
 * Runs once with {@code hms.demo.staff.enabled=true} (env HMS_DEMO_STAFF_ENABLED) and a shared password in
 * {@code hms.demo.staff.password} (env HMS_DEMO_STAFF_PASSWORD; never in source). A marker in system_settings stops
 * a second run. Only employees without an account are used, so existing logins are never touched.
 */
@Component
@ConditionalOnProperty(name = "hms.demo.staff.enabled", havingValue = "true")
@Order(Ordered.LOWEST_PRECEDENCE)   // after DemoDataSeeder, which creates the employees
public class StaffAccountSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StaffAccountSeeder.class);
    static final String MARKER = "demo.staff_accounts_at";

    /** Role → (employee staff category, how many accounts). Front desk and HR both come from ADMIN staff. */
    static final Map<String, Object[]> PLAN = new LinkedHashMap<>();
    static {
        PLAN.put("DOCTOR", new Object[] {"DOCTOR", 12});
        PLAN.put("NURSE", new Object[] {"NURSE", 14});
        PLAN.put("RECEPTIONIST", new Object[] {"ADMIN", 4});
        PLAN.put("LAB_STAFF", new Object[] {"LAB", 3});
        PLAN.put("PHARMACIST", new Object[] {"PHARMACY", 3});
        PLAN.put("ACCOUNTANT", new Object[] {"FINANCE", 2});
        PLAN.put("HR_MANAGER", new Object[] {"ADMIN", 2});
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final String password;

    public StaffAccountSeeder(JdbcTemplate jdbc, TransactionTemplate tx, UserRepository users, RoleRepository roles,
                              PasswordEncoder encoder, @Value("${hms.demo.staff.password:}") String password) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer done = jdbc.queryForObject("SELECT COUNT(*) FROM system_settings WHERE setting_key = ?", Integer.class, MARKER);
        if (done != null && done > 0) {
            log.info("Demo staff accounts already created; nothing to do.");
            return;
        }
        if (password.isBlank() || !PasswordPolicy.violations(password, "").isEmpty()) {
            log.warn("Demo staff accounts NOT created: set HMS_DEMO_STAFF_PASSWORD (10+ characters, a letter and a number).");
            return;
        }
        Integer employees = jdbc.queryForObject("SELECT COUNT(*) FROM employees", Integer.class);
        if (employees == null || employees == 0) {
            log.warn("Demo staff accounts NOT created: no employees yet. Seed the demo data first (HMS_DEMO_SEED).");
            return;
        }
        List<String> created = tx.execute(s -> create());
        log.info("Demo staff accounts created ({}): {}", created.size(), created);
    }

    private List<String> create() {
        String hash = encoder.encode(password);
        Instant now = Instant.now();
        Set<Long> used = new HashSet<>();
        List<String> created = new ArrayList<>();
        PLAN.forEach((roleCode, plan) -> {
            Role role = roles.findByCode(roleCode).orElseThrow();
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT id, first_name, last_name FROM employees
                    WHERE staff_category = ? AND status = 'ACTIVE' AND user_id IS NULL
                    ORDER BY id LIMIT ?""", plan[0], (Integer) plan[1] + used.size());
            int made = 0;
            for (Map<String, Object> row : rows) {
                long employeeId = ((Number) row.get("id")).longValue();
                if (made == (Integer) plan[1] || !used.add(employeeId)) {
                    continue;
                }
                String first = (String) row.get("first_name");
                String last = (String) row.get("last_name");
                User user = new User();
                user.setUsername(uniqueUsername(first, last));
                user.setFullName((roleCode.equals("DOCTOR") ? "Dr. " : "") + first + " " + last);
                user.setPasswordHash(hash);
                user.setMustChangePassword(false);
                user.setPasswordChangedAt(now);
                user.setStatus(UserStatus.ACTIVE);
                user.setRoles(new HashSet<>(Set.of(role)));
                users.saveAndFlush(user);
                jdbc.update("UPDATE employees SET user_id = ? WHERE id = ?", user.getId(), employeeId);
                created.add(user.getUsername() + " (" + roleCode + ")");
                made++;
            }
        });
        jdbc.update("INSERT INTO system_settings (setting_key, setting_value, value_type, description) VALUES (?, ?, 'STRING', ?)",
                MARKER, now.toString(), "Set by the demo staff-account seeder; delete to allow another run");
        return created;
    }

    private String uniqueUsername(String first, String last) {
        String base = User.normaliseUsername((first + "." + last).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.]", ""));
        String name = base;
        for (int n = 2; users.existsByUsername(name); n++) {
            name = base + n;
        }
        return name;
    }
}
