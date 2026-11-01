package com.hms.appointment;

import com.hms.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentReminderMailService {

    private final JavaMailSender mailSender;
    private final AppProperties appProperties;

    public boolean sendReminderEmail(Appointment appointment) {
        if (appointment == null || appointment.getPatient() == null) {
            return false;
        }

        String recipientEmail = appointment.getPatient().getEmail();
        if (recipientEmail == null || recipientEmail.isBlank()) {
            log.info("Skipping email reminder for appointment {} - no email registered for patient {}",
                    appointment.getAppointmentNumber(), appointment.getPatient().getMrn());
            return false;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(appProperties.mail().from());
            message.setTo(recipientEmail);
            message.setSubject("Appointment Reminder - " + appointment.getAppointmentNumber());

            String body = String.format(
                    """
                    Dear %s,

                    This is a reminder for your upcoming medical consultation at Hospital Management System:

                    • Appointment Number: %s
                    • Token Number: %d
                    • Doctor: %s (%s)
                    • Department: %s
                    • Date: %s
                    • Time: %s - %s

                    Please arrive at least 15 minutes prior to your allocated slot for triage and check-in.
                    If you wish to reschedule or cancel, please contact reception promptly.

                    Sincerely,
                    Hospital Outpatient Care Team
                    """,
                    appointment.getPatient().getFullName(),
                    appointment.getAppointmentNumber(),
                    appointment.getTokenNumber(),
                    appointment.getDoctor().getUser().getFullName(),
                    appointment.getDoctor().getSpecialization(),
                    appointment.getDepartment().getName(),
                    appointment.getAppointmentDate(),
                    appointment.getStartTime(),
                    appointment.getEndTime()
            );

            message.setText(body);
            mailSender.send(message);
            log.info("Sent appointment reminder email to {} for appointment {}",
                    recipientEmail, appointment.getAppointmentNumber());
            return true;
        } catch (Exception e) {
            log.error("Failed to send appointment reminder email to {}: {}", recipientEmail, e.getMessage());
            return false;
        }
    }
}

