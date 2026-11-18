-- V12  Per-IP sign-in throttling (docs/07 security checklist).
-- Account lockout stops guessing one password; this stops one address trying many accounts.
ALTER TABLE login_attempts ADD KEY ix_login_attempts_ip_time (ip_address, attempted_at);

INSERT INTO system_settings (setting_key, setting_value, value_type, description) VALUES
 ('security.ip_max_failed_logins', '30', 'INT', 'Failed sign-ins from one IP address before it is paused'),
 ('security.ip_window_minutes',    '15', 'INT', 'Window (and pause) for the per-IP sign-in limit');
