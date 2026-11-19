package lk.hms.modules.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lk.hms.common.audit.AuditService;
import lk.hms.common.security.CurrentUser;
import lk.hms.common.security.HmsUserPrincipal;
import lk.hms.modules.auth.dto.AuthDtos.ChangePasswordRequest;
import lk.hms.modules.auth.dto.AuthDtos.CurrentUserResponse;
import lk.hms.modules.auth.dto.AuthDtos.LoginRequest;
import lk.hms.modules.auth.service.AuthService;
import lk.hms.modules.auth.service.AuthService.ClientInfo;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuditService audit;
    private final SecurityContextRepository contextRepository;
    private final CsrfTokenRepository csrfRepository;
    private final FindByIndexNameSessionRepository<? extends Session> sessions;
    private final lk.hms.modules.doctors.repository.DoctorRepository doctors;
    private final lk.hms.modules.users.service.UserService userService;

    public AuthController(AuthService authService, AuditService audit, SecurityContextRepository contextRepository,
                          CsrfTokenRepository csrfRepository,
                          FindByIndexNameSessionRepository<? extends Session> sessions,
                          lk.hms.modules.doctors.repository.DoctorRepository doctors,
                          lk.hms.modules.users.service.UserService userService) {
        this.authService = authService;
        this.audit = audit;
        this.contextRepository = contextRepository;
        this.csrfRepository = csrfRepository;
        this.sessions = sessions;
        this.doctors = doctors;
        this.userService = userService;
    }

    private CurrentUserResponse me(lk.hms.common.security.HmsUserPrincipal p) {
        return CurrentUserResponse.from(p, doctors.findByUserId(p.getId()).map(d -> d.getId()).orElse(null));
    }

    /** Touching the deferred token makes Spring Security write the XSRF-TOKEN cookie. */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public CurrentUserResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
                                     HttpServletResponse response) {
        HmsUserPrincipal principal = authService.login(body.username(), body.password(), clientInfo(request));
        establishSession(principal, request, response);
        return me(principal);
    }

    @PostMapping("/sign-up")
    public ResponseEntity<Void> signUp(@Valid @RequestBody lk.hms.modules.users.dto.UserDtos.SignUpRequest body) {
        userService.signUp(body);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        CurrentUser.get().ifPresent(p -> audit.recordSecurityEvent("LOGOUT", p.getId(), p.getUsername(), "Signed out"));
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        csrfRepository.saveToken(null, request, response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public CurrentUserResponse me() {
        return me(CurrentUser.require());
    }

    /**
     * Changes the password, rotates the session id, and signs out the user's other sessions
     * (a password change should cut off anyone holding an old session).
     */
    @PostMapping("/change-password")
    public CurrentUserResponse changePassword(@Valid @RequestBody ChangePasswordRequest body,
                                              HttpServletRequest request, HttpServletResponse response) {
        HmsUserPrincipal current = CurrentUser.require();
        HmsUserPrincipal refreshed = authService.changePassword(current.getId(), body.currentPassword(), body.newPassword());
        // The stored row still carries the pre-rotation id until this request commits, so keep both ids.
        String previousId = request.getSession().getId();
        establishSession(refreshed, request, response);
        Set<String> keep = Set.of(previousId, request.getSession().getId());
        sessions.findByPrincipalName(refreshed.getUsername()).keySet().stream()
                .filter(id -> !keep.contains(id))
                .forEach(sessions::deleteById);
        return me(refreshed);
    }

    /** New session id (prevents session fixation), stores the context, rotates the CSRF token. */
    private void establishSession(HmsUserPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        if (request.getSession(false) != null) {
            request.changeSessionId();
        } else {
            request.getSession(true);
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        csrfRepository.saveToken(csrfRepository.generateToken(request), request, response);
    }

    private static ClientInfo clientInfo(HttpServletRequest request) {
        return new ClientInfo(request.getRemoteAddr(), request.getHeader("User-Agent"));
    }
}
