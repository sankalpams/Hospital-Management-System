package lk.hms.common.security;

import lk.hms.modules.users.entity.User;
import lk.hms.modules.users.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the principal from the database. Also stops Spring Boot from creating its default
 * in-memory user with a generated password.
 */
@Service
public class HmsUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public HmsUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public HmsUserPrincipal loadUserByUsername(String username) {
        return users.findWithAuthoritiesByUsername(User.normaliseUsername(username))
                .map(HmsUserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }
}
