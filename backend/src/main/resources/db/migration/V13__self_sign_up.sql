-- Self sign-up: a request creates a PENDING account that cannot sign in until an administrator activates it.
ALTER TABLE users DROP CHECK ck_users_status;
ALTER TABLE users ADD CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE','INACTIVE','PENDING'));
