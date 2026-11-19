package lk.hms.modules.users.controller;

import jakarta.validation.Valid;
import lk.hms.common.api.PageResponse;
import lk.hms.modules.users.dto.UserDtos.CreateUserRequest;
import lk.hms.modules.users.dto.UserDtos.RoleResponse;
import lk.hms.modules.users.dto.UserDtos.TemporaryPasswordResponse;
import lk.hms.modules.users.dto.UserDtos.UpdateUserRequest;
import lk.hms.modules.users.dto.UserDtos.UserResponse;
import lk.hms.modules.users.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('user:read')")
    public PageResponse<UserResponse> list(@RequestParam(required = false) String q,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) String role,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size,
                                           @RequestParam(required = false) String sort) {
        return service.search(q, status, role, page, size, sort);
    }

    /** Demo staff logins that still use the shared password set in HMS_DEMO_STAFF_PASSWORD. Super Admin only. */
    @GetMapping("/users/demo-staff")
    @PreAuthorize("hasAuthority('settings:manage')")   // held only by Super Admin
    public List<UserResponse> demoStaff() {
        return service.demoStaffLogins();
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasAuthority('user:read')")
    public UserResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('user:create')")
    public TemporaryPasswordResponse create(@Valid @RequestBody CreateUserRequest body) {
        return service.create(body);
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('user:update')")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest body) {
        return service.update(id, body);
    }

    @PostMapping("/users/{id}/activate")
    @PreAuthorize("hasAuthority('user:deactivate')")
    public UserResponse activate(@PathVariable Long id) {
        return service.setActive(id, true);
    }

    @PostMapping("/users/{id}/deactivate")
    @PreAuthorize("hasAuthority('user:deactivate')")
    public UserResponse deactivate(@PathVariable Long id) {
        return service.setActive(id, false);
    }

    @PostMapping("/users/{id}/reset-password")
    @PreAuthorize("hasAuthority('user:reset-password')")
    public TemporaryPasswordResponse resetPassword(@PathVariable Long id) {
        return service.resetPassword(id);
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('user:read')")
    public List<RoleResponse> roles() {
        return service.roles();
    }
}
