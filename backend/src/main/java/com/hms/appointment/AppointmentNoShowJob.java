package com.hms.appointment;

import com.hms.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentNoShowJob {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentStatusHistoryRepository historyRepository;
    private final AppointmentStateMachine stateMachine;
    private final AppProperties appProperties;

    /**
     * Runs every 15 minutes to mark expired un-checked-in appointments as NO_SHOW.
     */
    @Scheduled(cron = "0 */15 * * * *")
    public void runScheduledNoShowSweep() {
        processNoShows(LocalDate.now(), LocalTime.now());
    }

    @Transactional
    public int processNoShows(LocalDate currentDate, LocalTime currentTime) {
        int graceMinutes = (appProperties.appointment() != null && appProperties.appointment().noShowGraceMinutes() > 0)
                ? appProperties.appointment().noShowGraceMinutes()
                : 30;

        List<Appointment> candidateAppointments = appointmentRepository.findByAppointmentDateLessThanEqualAndStatusIn(
                currentDate,
                List.of(AppointmentStatus.SCHEDULED, AppointmentStatus.CONFIRMED)
        );

        int updatedCount = 0;
        for (Appointment apt : candidateAppointments) {
            boolean isExpiredPastDate = apt.getAppointmentDate().isBefore(currentDate);
            boolean isExpiredToday = apt.getAppointmentDate().isEqual(currentDate)
                    && apt.getEndTime().plusMinutes(graceMinutes).isBefore(currentTime);

            if (isExpiredPastDate || isExpiredToday) {
                if (stateMachine.isValidTransition(apt.getStatus(), AppointmentStatus.NO_SHOW)) {
                    AppointmentStatus previousStatus = apt.getStatus();
                    apt.setStatus(AppointmentStatus.NO_SHOW);
                    appointmentRepository.save(apt);

                    historyRepository.save(new AppointmentStatusHistory(
                            apt,
                            previousStatus,
                            AppointmentStatus.NO_SHOW,
                            "Auto-marked as NO_SHOW by scheduler after expiration of " + graceMinutes + " min grace period",
                            "system"
                    ));

                    log.info("Auto-marked appointment {} (Date: {}, Slot End: {}) as NO_SHOW",
                            apt.getAppointmentNumber(), apt.getAppointmentDate(), apt.getEndTime());
                    updatedCount++;
                }
            }
        }

        return updatedCount;
    }
}

