package lk.hms.modules.users.entity;

public enum UserStatus {
    ACTIVE,
    INACTIVE,
    /** Signed up, waiting for an administrator to activate. Cannot sign in. */
    PENDING
}
