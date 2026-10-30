package com.hms.user;

import com.hms.auth.AuthService;
import com.hms.auth.PasswordPolicy;
import com.hms.common.AppException;
import com.hms.role.Role;
import com.hms.role.RoleCode;
import com.hms.role.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository users;
    @Mock private RoleRepository roles;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UserMapper mapper;
    @Mock private AuthService authService;
    @Mock private PasswordPolicy passwordPolicy;

    private UserService userService;
    private Role doctorRole;
    private User testUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userService = new UserService(users, roles, passwordEncoder, mapper, authService, passwordPolicy);

        doctorRole = new Role(RoleCode.DOCTOR, "Doctor", "Medical doctor");
        userId = UUID.randomUUID();

        testUser = new User();
        testUser.setId(userId);
        testUser.setUsername("doctor1");
        testUser.setEmail("doctor1@hms.local");
        testUser.setFirstName("Sunil");
        testUser.setLastName("Perera");
        testUser.setRole(doctorRole);
        testUser.setActive(true);
    }

    @Test
    @DisplayName("Create user with unique username and email successfully persists user")
    void create_Success() {
        UserDtos.CreateUserRequest request = new UserDtos.CreateUserRequest(
                "newuser", "newuser@hms.local", "Kamal", "Silva",
                "+94771112233", RoleCode.DOCTOR, "Welcome@12345"
        );

        when(users.existsByUsernameIgnoreCase("newuser")).thenReturn(false);
        when(users.existsByEmailIgnoreCase("newuser@hms.local")).thenReturn(false);
        when(roles.findWithPermissionsByCode(RoleCode.DOCTOR)).thenReturn(Optional.of(doctorRole));
        when(passwordEncoder.encode(any())).thenReturn("hashedTempPassword");
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserDtos.UserResponse mapped = new UserDtos.UserResponse(
                UUID.randomUUID(), "newuser", "newuser@hms.local", "Kamal", "Silva",
                "+94771112233", RoleCode.DOCTOR, true, true, null, Instant.now(), 0L
        );
        when(mapper.toResponse(any(User.class))).thenReturn(mapped);

        UserDtos.UserResponse result = userService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.username()).isEqualTo("newuser");
        verify(passwordPolicy).validate("Welcome@12345");
        verify(users).save(any(User.class));
    }

    @Test
    @DisplayName("Create user with existing username throws conflict exception")
    void create_DuplicateUsername_ThrowsConflict() {
        UserDtos.CreateUserRequest request = new UserDtos.CreateUserRequest(
                "existinguser", "unique@hms.local", "Kamal", "Silva",
                "+94771112233", RoleCode.DOCTOR, "Welcome@12345"
        );

        when(users.existsByUsernameIgnoreCase("existinguser")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(appEx.getCode()).isEqualTo("CONFLICT");
                });
    }

    @Test
    @DisplayName("Create user with existing email throws conflict exception")
    void create_DuplicateEmail_ThrowsConflict() {
        UserDtos.CreateUserRequest request = new UserDtos.CreateUserRequest(
                "newuser", "duplicate@hms.local", "Kamal", "Silva",
                "+94771112233", RoleCode.DOCTOR, "Welcome@12345"
        );

        when(users.existsByUsernameIgnoreCase("newuser")).thenReturn(false);
        when(users.existsByEmailIgnoreCase("duplicate@hms.local")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
    }

    @Test
    @DisplayName("Deactivate user marks user inactive")
    void deactivate_OtherUser_Success() {
        UUID actorId = UUID.randomUUID();
        when(users.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(testUser));

        UserDtos.UserResponse mapped = new UserDtos.UserResponse(
                userId, testUser.getUsername(), testUser.getEmail(), testUser.getFirstName(),
                testUser.getLastName(), testUser.getPhone(), RoleCode.DOCTOR, false,
                false, null, Instant.now(), 0L
        );
        when(mapper.toResponse(testUser)).thenReturn(mapped);

        UserDtos.UserResponse response = userService.deactivate(userId, actorId);

        assertThat(testUser.isActive()).isFalse();
        assertThat(response.active()).isFalse();
    }

    @Test
    @DisplayName("Deactivate own account throws business rule violation exception")
    void deactivate_OwnAccount_ThrowsBusinessException() {
        assertThatThrownBy(() -> userService.deactivate(userId, userId))
                .isInstanceOf(AppException.class)
                .hasMessage("You cannot deactivate your own account.");
    }

    @Test
    @DisplayName("Soft delete marks user inactive and sets deletedAt timestamp")
    void delete_OtherUser_SetsDeletedAt() {
        UUID actorId = UUID.randomUUID();
        when(users.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(testUser));

        userService.delete(userId, actorId);

        assertThat(testUser.isActive()).isFalse();
        assertThat(testUser.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("Delete own account throws business rule violation exception")
    void delete_OwnAccount_ThrowsBusinessException() {
        assertThatThrownBy(() -> userService.delete(userId, userId))
                .isInstanceOf(AppException.class)
                .hasMessage("You cannot delete your own account.");
    }
}

