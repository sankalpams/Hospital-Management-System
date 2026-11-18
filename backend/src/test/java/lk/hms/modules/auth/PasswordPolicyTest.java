package lk.hms.modules.auth;

import lk.hms.common.exception.BusinessException;
import lk.hms.modules.auth.service.PasswordPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    @Test
    void acceptsAStrongPassword() {
        assertThat(PasswordPolicy.violations("Kandy-Lake-2026", "nimal")).isEmpty();
    }

    @Test
    void rejectsShortPasswords() {
        assertThat(PasswordPolicy.violations("abc12", "u")).anyMatch(m -> m.contains("10 characters"));
    }

    @Test
    void requiresLetterAndDigit() {
        assertThat(PasswordPolicy.violations("1234567890", "u")).anyMatch(m -> m.contains("letter"));
        assertThat(PasswordPolicy.violations("abcdefghijk", "u")).anyMatch(m -> m.contains("number"));
    }

    @Test
    void rejectsPasswordContainingUsername() {
        assertThat(PasswordPolicy.violations("xxNimal2026xx", "nimal")).anyMatch(m -> m.contains("username"));
    }

    @Test
    void rejectsOverlongPasswordsBecauseBcryptTruncates() {
        assertThat(PasswordPolicy.violations("a1".repeat(40), "u")).anyMatch(m -> m.contains("at most"));
    }

    @Test
    void validateThrowsBusinessException() {
        assertThatThrownBy(() -> PasswordPolicy.validate("weak", "u"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "WEAK_PASSWORD");
    }
}
