package com.hms.user;

import com.hms.auth.AuthDtos;
import com.hms.auth.LoginHistoryRepository;
import com.hms.common.PageResponse;
import com.hms.common.PageableFactory;
import com.hms.role.RoleCode;
import com.hms.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/users") @RequiredArgsConstructor
public class UserController {
    private final UserService users;
    private final LoginHistoryRepository loginHistory;
    @GetMapping @PreAuthorize("hasAuthority('USER_READ')")
    public PageResponse<UserDtos.UserResponse> list(@RequestParam(required = false) String q, @RequestParam(required = false) RoleCode role,
        @RequestParam(required = false) Boolean active, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size,
        @RequestParam(defaultValue = "lastName,asc") String sort) { return users.list(q, role, active, page, size, sort); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('USER_MANAGE')")
    public UserDtos.UserResponse create(@Valid @RequestBody UserDtos.CreateUserRequest request) { return users.create(request); }
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('USER_READ')")
    public UserDtos.UserResponse get(@PathVariable UUID id) { return users.get(id); }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('USER_MANAGE')")
    public UserDtos.UserResponse update(@PathVariable UUID id, @Valid @RequestBody UserDtos.UpdateUserRequest request) { return users.update(id, request); }
    @PostMapping("/{id}/deactivate") @PreAuthorize("hasAuthority('USER_MANAGE')")
    public UserDtos.UserResponse deactivate(@PathVariable UUID id) { return users.deactivate(id, actor()); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('USER_MANAGE')")
    public void delete(@PathVariable UUID id) { users.delete(id, actor()); }
    @PostMapping("/{id}/reset-password") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('USER_MANAGE')")
    public void reset(@PathVariable UUID id) { users.sendAdminReset(id); }
    @PutMapping("/me") public UserDtos.UserResponse profile(@Valid @RequestBody UserDtos.UpdateUserRequest request) { return users.updateProfile(actor(), request); }
    @GetMapping("/{id}/login-history") @PreAuthorize("hasAuthority('USER_READ')")
    public PageResponse<AuthDtos.LoginHistoryResponse> history(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return PageResponse.from(loginHistory.findByUserIdOrderByOccurredAtDesc(id, PageableFactory.of(page, size, "occurredAt,desc")).map(h -> new AuthDtos.LoginHistoryResponse(h.getId(), h.getUsernameAttempted(), h.getOutcome(), h.getIpAddress(), h.getOccurredAt())));
    }
    private UUID actor() { return CurrentUser.id().orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("No user")); }
}
