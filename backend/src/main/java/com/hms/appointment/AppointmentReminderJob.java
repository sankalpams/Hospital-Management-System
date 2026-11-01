package com.hms.appointment;

import com.hms.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentReminderJob {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentReminderMailService mailService;
    private final SmsNotificationService smsService;
    private final AppProperties appProperties;

    /**
     * Runs hourly to process upcoming appointment reminders 24h prior.
     */
    @Scheduled(cron = "0 0 * * * *")
    public void runScheduledReminders() {
        int leadHours = (appProperties.appointment() != null && appProperties.appointment().reminderLeadHours() > 0)
                ? appProperties.appointment().reminderLeadHours()
                : 24;

        LocalDate targetDate = LocalDate.now().plusDays(leadHours / 24 > 0 ? leadHours / 24 : 1);
        processRemindersForDate(targetDate);
    }

    @Transactional
    public int processRemindersForDate(LocalDate date) {
        List<Appointment> pendingAppointments = appointmentRepository.findByAppointmentDateAndReminderStatusAndStatusIn(
                date,
                ReminderStatus.PENDING,
                List.of(AppointmentStatus.SCHEDULED, AppointmentStatus.CONFIRMED)
        );

        if (pendingAppointments.isEmpty()) {
            return 0;
        }

        log.info("Processing {} upcoming appointment reminders for date {}", pendingAppointments.size(), date);
        int sentCount = 0;

        for (Appointment appointment : pendingAppointments) {
            boolean emailSent = mailService.sendReminderEmail(appointment);

            String smsMessage = String.format(
                    "HMS Reminder: Appt #%s with Dr. %s on %s at %s. Token: %d.",
                    appointment.getAppointmentNumber(),
                    appointment.getDoctor().getUser().getLastName(),
                    appointment.getAppointmentDate(),
                    appointment.getStartTime(),
                    appointment.getTokenNumber()
            );
            boolean smsSent = smsService.sendSms(appointment.getPatient().getPhone(), smsMessage);

            if (emailSent || smsSent) {
                appointment.setReminderSentAt(OffsetDateTime.now());
                appointment.setReminderStatus(ReminderStatus.SENT);
                sentCount++;
            } else {
                appointment.setReminderStatus(ReminderStatus.FAILED);
            }
            appointmentRepository.save(appointment);
        }

        log.info("Finished dispatching reminders for date {}: {} sent", date, sentCount);
        return sentCount;
    }
}

