package com.hms.appointment;

import com.hms.common.AppException;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

@Component
public class AppointmentStateMachine {

    private static final Map<AppointmentStatus, Set<AppointmentStatus>> VALID_TRANSITIONS = new EnumMap<>(AppointmentStatus.class);

    static {
        VALID_TRANSITIONS.put(AppointmentStatus.SCHEDULED, Set.of(
                AppointmentStatus.CONFIRMED,
                AppointmentStatus.CHECKED_IN,
                AppointmentStatus.CANCELLED,
                AppointmentStatus.RESCHEDULED,
                AppointmentStatus.NO_SHOW
        ));

        VALID_TRANSITIONS.put(AppointmentStatus.CONFIRMED, Set.of(
                AppointmentStatus.CHECKED_IN,
                AppointmentStatus.CANCELLED,
                AppointmentStatus.RESCHEDULED,
                AppointmentStatus.NO_SHOW
        ));

        VALID_TRANSITIONS.put(AppointmentStatus.CHECKED_IN, Set.of(
                AppointmentStatus.IN_CONSULTATION,
                AppointmentStatus.CANCELLED,
                AppointmentStatus.NO_SHOW
        ));

        VALID_TRANSITIONS.put(AppointmentStatus.IN_CONSULTATION, Set.of(
                AppointmentStatus.COMPLETED
        ));

        // Terminal states have no valid subsequent transitions
        VALID_TRANSITIONS.put(AppointmentStatus.COMPLETED, Collections.emptySet());
        VALID_TRANSITIONS.put(AppointmentStatus.CANCELLED, Collections.emptySet());
        VALID_TRANSITIONS.put(AppointmentStatus.NO_SHOW, Collections.emptySet());
        VALID_TRANSITIONS.put(AppointmentStatus.RESCHEDULED, Collections.emptySet());
    }

    public boolean isValidTransition(AppointmentStatus from, AppointmentStatus to) {
        if (from == null || to == null) {
            return false;
        }
        if (from == to) {
            return false;
        }
        Set<AppointmentStatus> allowed = VALID_TRANSITIONS.getOrDefault(from, Collections.emptySet());
        return allowed.contains(to);
    }

    public void validateTransition(AppointmentStatus from, AppointmentStatus to) {
        if (!isValidTransition(from, to)) {
            throw AppException.unprocessableEntity(
                    String.format("Invalid appointment status transition from %s to %s", from, to)
            );
        }
    }

    public Set<AppointmentStatus> getValidNextStatuses(AppointmentStatus current) {
        if (current == null) {
            return Collections.emptySet();
        }
        return VALID_TRANSITIONS.getOrDefault(current, Collections.emptySet());
    }
}

