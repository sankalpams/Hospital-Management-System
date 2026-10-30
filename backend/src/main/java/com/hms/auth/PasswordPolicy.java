package com.hms.auth;

import com.hms.common.AppException;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {
    public void validate(String password) {
        if (password == null || password.length() < 12 || !password.matches(".*[A-Z].*") || !password.matches(".*[a-z].*") ||
            !password.matches(".*\\d.*") || !password.matches(".*[^A-Za-z0-9].*")) {
            throw AppException.business("Password must be at least 12 characters and include upper- and lower-case letters, a number, and a symbol.");
        }
    }
}
