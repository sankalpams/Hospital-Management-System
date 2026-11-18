package lk.hms.common.security;

import lk.hms.modules.users.entity.Permission;
import lk.hms.modules.users.entity.Role;
import lk.hms.modules.users.entity.User;
import lk.hms.modules.users.entity.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Authenticated user held in the server-side session. Authorities are permission codes
 * (e.g. {@code patient:create}) plus {@code ROLE_<code>} entries. No password is kept.
 * Must stay Serializable: Spring Session JDBC stores it in SPRING_SESSION_ATTRIBUTES.
 */
public final class HmsUserPrincipal implements UserDetails, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String username;
    private final String fullName;
    private final Set<String> roles;
    private final Set<String> permissions;
    private final boolean mustChangePassword;
    private final boolean active;

    private HmsUserPrincipal(Long id, String username, String fullName, Set<String> roles,
                             Set<String> permissions, boolean mustChangePassword, boolean active) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.roles = roles;
        this.permissions = permissions;
        this.mustChangePassword = mustChangePassword;
        this.active = active;
    }

    /** Requires roles and their permissions to be loaded (see UserRepository#findWithAuthoritiesByUsername). */
    public static HmsUserPrincipal from(User user) {
        TreeSet<String> roleCodes = user.getRoles().stream().map(Role::getCode)
                .collect(Collectors.toCollection(TreeSet::new));
        TreeSet<String> permissionCodes = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(Permission::getCode)
                .collect(Collectors.toCollection(TreeSet::new));
        return new HmsUserPrincipal(user.getId(), user.getUsername(), user.getFullName(),
                Collections.unmodifiableSortedSet(roleCodes), Collections.unmodifiableSortedSet(permissionCodes),
                user.isMustChangePassword(), user.getStatus() == UserStatus.ACTIVE);
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new java.util.ArrayList<>();
        permissions.forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
        roles.forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
        return authorities;
    }

    /** The hash is never held in the session; authentication is done in AuthService. */
    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
