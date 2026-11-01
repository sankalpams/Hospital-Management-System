package com.hms.appointment;

import com.hms.common.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppointmentStateMachineTest {

    private AppointmentStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new AppointmentStateMachine();
    }

    @ParameterizedTest
    @CsvSource({
            "SCHEDULED, CONFIRMED",
            "SCHEDULED, CHECKED_IN",
            "SCHEDULED, CANCELLED",
            "SCHEDULED, RESCHEDULED",
            "SCHEDULED, NO_SHOW",
            "CONFIRMED, CHECKED_IN",
            "CONFIRMED, CANCELLED",
            "CONFIRMED, RESCHEDULED",
            "CONFIRMED, NO_SHOW",
            "CHECKED_IN, IN_CONSULTATION",
            "CHECKED_IN, CANCELLED",
            "CHECKED_IN, NO_SHOW",
            "IN_CONSULTATION, COMPLETED"
    })
    @DisplayName("Valid status transitions are approved")
    void testValidTransitions(AppointmentStatus from, AppointmentStatus to) {
        assertThat(stateMachine.isValidTransition(from, to)).isTrue();
        stateMachine.validateTransition(from, to); // does not throw
    }

    @ParameterizedTest
    @CsvSource({
            "SCHEDULED, COMPLETED",
            "SCHEDULED, IN_CONSULTATION",
            "CONFIRMED, COMPLETED",
            "CHECKED_IN, COMPLETED",
            "COMPLETED, SCHEDULED",
            "COMPLETED, CONFIRMED",
            "COMPLETED, CANCELLED",
            "CANCELLED, SCHEDULED",
            "CANCELLED, CHECKED_IN",
            "NO_SHOW, SCHEDULED",
            "NO_SHOW, CHECKED_IN",
            "RESCHEDULED, SCHEDULED",
            "SCHEDULED, SCHEDULED",
            "COMPLETED, COMPLETED"
    })
    @DisplayName("Invalid status transitions are rejected with 422 Unprocessable Entity")
    void testInvalidTransitions(AppointmentStatus from, AppointmentStatus to) {
        assertThat(stateMachine.isValidTransition(from, to)).isFalse();

        assertThatThrownBy(() -> stateMachine.validateTransition(from, to))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getStatus().value()).isEqualTo(422);
                    assertThat(appEx.getMessage()).contains("Invalid appointment status transition");
                });
    }

    @Test
    @DisplayName("Terminal states have empty next statuses")
    void testTerminalStates() {
        assertThat(stateMachine.getValidNextStatuses(AppointmentStatus.COMPLETED)).isEmpty();
        assertThat(stateMachine.getValidNextStatuses(AppointmentStatus.CANCELLED)).isEmpty();
        assertThat(stateMachine.getValidNextStatuses(AppointmentStatus.NO_SHOW)).isEmpty();
        assertThat(stateMachine.getValidNextStatuses(AppointmentStatus.RESCHEDULED)).isEmpty();
    }
}

