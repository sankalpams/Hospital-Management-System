package lk.hms.support;

import lk.hms.modules.users.entity.User;
import lk.hms.modules.users.entity.UserStatus;
import lk.hms.modules.users.repository.RoleRepository;
import lk.hms.modules.users.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Creates uniquely named users so tests stay independent of each other. */
@Component
public class TestUsers {

    public static final String PASSWORD = "Correct-Horse-42";

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;

    public TestUsers(UserRepository users, RoleRepository roles, PasswordEncoder encoder) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
    }

    @Transactional
    public User create(String roleCode, boolean mustChangePassword, UserStatus status) {
        User u = new User();
        u.setUsername("t_" + UUID.randomUUID().toString().substring(0, 12));
        u.setFullName("Test " + roleCode);
        u.setPasswordHash(encoder.encode(PASSWORD));
        u.setMustChangePassword(mustChangePassword);
        u.setStatus(status);
        u.getRoles().add(roles.findByCode(roleCode).orElseThrow());
        return users.save(u);
    }

    public User active(String roleCode) {
        return create(roleCode, false, UserStatus.ACTIVE);
    }
}
