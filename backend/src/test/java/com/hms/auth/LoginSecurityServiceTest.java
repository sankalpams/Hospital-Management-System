package com.hms.auth;

import com.hms.config.AppProperties;
import com.hms.user.User;
import com.hms.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginSecurityServiceTest {

    @Mock private UserRepository users;
    @Mock private LoginHistoryRepository history;

    private AppProperties properties;
    private LoginSecurityService loginSecurityService;

    private User testUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        properties = new AppProperties(
                new AppProperties.Cors("http://localhost:8080"),
                new AppProperties.Security("ZGV2LXNlY3JldC1tdXN0LWJlLWF0LWxlYXN0LTMyLWJ5dGVzLWxvbmc=", 15, 7, 15, 5),
                new AppProperties.Mail("no-reply@hms.local", "http://localhost:8080/reset-password"),
                new AppProperties.Storage("./target/test-uploads")
        );
        loginSecurityService = new LoginSecurityService(users, history, properties);

        userId = UUID.randomUUID();
        testUser = new User();
        testUser.setId(userId);
        testUser.setUsername("doctor");
        testUser.setActive(true);
        testUser.setFailedLoginAttempts((short) 0);
    }

    @Test
    @DisplayName("Failed login attempt increments counter and records failure history")
    void recordFailure_IncrementsCounter() {
        when(users.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(testUser));

        loginSecurityService.recordFailure(userId, "doctor", "127.0.0.1", "TestAgent");

        assertThat(testUser.getFailedLoginAttempts()).isEqualTo((short) 1);
        assertThat(testUser.getLockedUntil()).isNull();

        ArgumentCaptor<LoginHistory> captor = ArgumentCaptor.forClass(LoginHistory.class);
        verify(history).save(captor.capture());
        LoginHistory recorded = captor.getValue();
        assertThat(recorded.getOutcome()).isEqualTo(LoginOutcome.FAILURE);
        assertThat(recorded.getUsernameAttempted()).isEqualTo("doctor");
        assertThat(recorded.getIpAddress()).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("Five consecutive failed login attempts trigger account lockout")
    void recordFailure_FifthAttempt_LocksAccount() {
        testUser.setFailedLoginAttempts((short) 4);
        when(users.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(testUser));

        loginSecurityService.recordFailure(userId, "doctor", "127.0.0.1", "TestAgent");

        assertThat(testUser.getFailedLoginAttempts()).isEqualTo((short) 5);
        assertThat(testUser.getLockedUntil()).isNotNull();
    }
}

