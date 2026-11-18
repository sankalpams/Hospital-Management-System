package lk.hms.modules.auth.service;

import lk.hms.config.HmsProperties;
import lk.hms.modules.users.entity.Role;
import lk.hms.modules.users.entity.User;
import lk.hms.modules.users.repository.RoleRepository;
import lk.hms.modules.users.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Creates the first Super Admin from HMS_BOOTSTRAP_ADMIN_* environment variables, only when the
 * users table is empty. The account must change its password on first sign-in. No credentials
 * exist in source code or migrations.
 */
@Component
public class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final HmsProperties properties;

    public BootstrapAdminRunner(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
                                HmsProperties properties) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        HmsProperties.BootstrapAdmin admin = properties.bootstrapAdmin();
        if (admin == null || !admin.isConfigured()) {
            log.warn("No users exist and HMS_BOOTSTRAP_ADMIN_USERNAME / HMS_BOOTSTRAP_ADMIN_PASSWORD are not set. "
                    + "Set them and restart to create the first Super Admin.");
            return;
        }
        String username = User.normaliseUsername(admin.username());
        List<String> problems = PasswordPolicy.violations(admin.password(), username);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("HMS_BOOTSTRAP_ADMIN_PASSWORD does not meet the password policy: "
                    + String.join(" ", problems));
        }
        Role superAdmin = roles.findByCode(Role.SUPER_ADMIN)
                .orElseThrow(() -> new IllegalStateException("SUPER_ADMIN role missing; did Flyway V9 run?"));

        User user = new User();
        user.setUsername(username);
        user.setFullName(admin.fullName() == null || admin.fullName().isBlank() ? "System Administrator" : admin.fullName());
        user.setPasswordHash(passwordEncoder.encode(admin.password()));
        user.setMustChangePassword(true);
        user.getRoles().add(superAdmin);
        users.save(user);
        log.info("Created bootstrap Super Admin '{}'. A password change is required at first sign-in.", username);
    }
}
