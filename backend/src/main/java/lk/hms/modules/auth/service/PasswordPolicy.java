package lk.hms.modules.auth.service;

import lk.hms.common.exception.BusinessException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Server-side password rules (docs/01 §2A). The client mirrors them for instant feedback only. */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 72;   // BCrypt only uses the first 72 bytes

    private PasswordPolicy() {
    }

    public static List<String> violations(String password, String username) {
        List<String> problems = new ArrayList<>();
        if (password == null || password.length() < MIN_LENGTH) {
            problems.add("Use at least " + MIN_LENGTH + " characters.");
        }
        if (password != null && password.length() > MAX_LENGTH) {
            problems.add("Use at most " + MAX_LENGTH + " characters.");
        }
        if (password != null && password.chars().noneMatch(Character::isLetter)) {
            problems.add("Include at least one letter.");
        }
        if (password != null && password.chars().noneMatch(Character::isDigit)) {
            problems.add("Include at least one number.");
        }
        if (password != null && username != null && !username.isBlank()
                && password.toLowerCase(Locale.ROOT).contains(username.toLowerCase(Locale.ROOT))) {
            problems.add("Do not include your username.");
        }
        return problems;
    }

    public static void validate(String password, String username) {
        List<String> problems = violations(password, username);
        if (!problems.isEmpty()) {
            throw BusinessException.badRequest("WEAK_PASSWORD", String.join(" ", problems));
        }
    }
}
