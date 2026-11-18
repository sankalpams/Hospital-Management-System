package lk.hms.common.security;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/**
 * Ends every server-side session of a user. Used when an account is deactivated, its roles
 * change (permissions are cached in the session) or an administrator resets its password.
 */
@Component
public class SessionRevoker {

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public SessionRevoker(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    public int revokeAll(String username) {
        var ids = sessions.findByPrincipalName(username).keySet();
        ids.forEach(sessions::deleteById);
        return ids.size();
    }
}
