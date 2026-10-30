package com.hms.auth;

import com.hms.common.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    private PasswordPolicy passwordPolicy;

    @BeforeEach
    void setUp() {
        passwordPolicy = new PasswordPolicy();
    }

    @Test
    @DisplayName("Valid password meeting all complexity rules passes validation")
    void validPassword() {
        assertThatCode(() -> passwordPolicy.validate("Welcome@12345"))
                .doesNotThrowAnyException();
        assertThatCode(() -> passwordPolicy.validate("P@ssw0rdSecure!2026"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Short@1",             // < 12 chars
            "nocapitalletter@123", // missing uppercase
            "NOLOWERCASELETTER@123",// missing lowercase
            "NoDigitsAllowed!@#",  // missing digit
            "NoSpecialCharacter12" // missing special character
    })
    @DisplayName("Invalid passwords violating length or character requirements are rejected")
    void invalidPasswords(String password) {
        assertThatThrownBy(() -> passwordPolicy.validate(password))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("at least 12 characters");
    }

    @Test
    @DisplayName("Null password throws validation exception")
    void nullPassword() {
        assertThatThrownBy(() -> passwordPolicy.validate(null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("at least 12 characters");
    }
}

