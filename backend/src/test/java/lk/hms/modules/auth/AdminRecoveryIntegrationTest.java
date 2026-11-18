package lk.hms.modules.auth;

import lk.hms.common.exception.BusinessException;
import lk.hms.modules.auth.service.AdminRecovery;
import lk.hms.modules.users.entity.User;
import lk.hms.modules.users.entity.UserStatus;
import lk.hms.support.Api;
import lk.hms.support.IntegrationTest;
import lk.hms.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class AdminRecoveryIntegrationTest {

    @Autowired AdminRecovery recovery;
    @Autowired TestUsers testUsers;
    @Autowired Api api;
    @Autowired JdbcTemplate jdbc;

    @Test
    void issuesATemporaryPasswordForALockedOrDeactivatedSuperAdmin() throws Exception {
        User admin = testUsers.create("SUPER_ADMIN", false, UserStatus.INACTIVE);
        jdbc.update("UPDATE users SET locked_until = DATE_ADD(UTC_TIMESTAMP(), INTERVAL 1 DAY) WHERE id = ?", admin.getId());

        String temporary = recovery.recover(admin.getUsername().toUpperCase());
        assertThat(temporary).hasSizeGreaterThanOrEqualTo(12);

        Api.Session s = api.login(admin.getUsername(), temporary, admin);
        assertThat(s.get("/api/v1/auth/me").body().get("mustChangePassword").asBoolean()).isTrue();
        assertThat(s.get("/api/v1/patients").status()).as("nothing else until the password is changed").isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'ADMIN_RECOVERY' AND entity_type = 'USER' AND entity_id = ? AND actor_user_id IS NULL", Integer.class, admin.getId()))
                .isEqualTo(1);
        assertThatThrownBy(() -> api.login(admin.getUsername(), TestUsers.PASSWORD, admin)).as("old password no longer works")
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesOtherAccountsAndUnknownNames() {
        User nurse = testUsers.active("NURSE");
        assertThatThrownBy(() -> recovery.recover(nurse.getUsername())).isInstanceOf(BusinessException.class).hasMessageContaining("not a Super Admin");
        assertThatThrownBy(() -> recovery.recover("nobody-here")).isInstanceOf(BusinessException.class).hasMessageContaining("No account");
    }
}
