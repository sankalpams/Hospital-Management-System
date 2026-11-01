package com.hms.appointment;

import com.hms.config.AppProperties;
import com.hms.department.Department;
import com.hms.doctor.Doctor;
import com.hms.patient.Patient;
import com.hms.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentJobsTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AppointmentStatusHistoryRepository historyRepository;

    @Mock
    private AppointmentReminderMailService mailService;

    @Mock
    private SmsNotificationService smsService;

    private AppointmentReminderJob reminderJob;
    private AppointmentNoShowJob noShowJob;
    private AppointmentStateMachine stateMachine;

    private Doctor doctor;
    private Patient patient;
    private Department department;

    @BeforeEach
    void setUp() {
        stateMachine = new AppointmentStateMachine();

        AppProperties appProperties = new AppProperties(
                new AppProperties.Cors("http://localhost:5173"),
                new AppProperties.Security("secret", 15, 7, 15, 5),
                new AppProperties.Mail("no-reply@hms.local", "http://reset"),
                new AppProperties.Storage("./uploads"),
                new AppProperties.Appointment(30, 24)
        );

        reminderJob = new AppointmentReminderJob(appointmentRepository, mailService, smsService, appProperties);
        noShowJob = new AppointmentNoShowJob(appointmentRepository, historyRepository, stateMachine, appProperties);

        User doctorUser = new User();
        doctorUser.setFirstName("Nimal");
        doctorUser.setLastName("Fernando");

        department = new Department("CARD", "Cardiology", "Cardiology", "+94112345678", "Floor 1");

        doctor = new Doctor();
        doctor.setId(UUID.randomUUID());
        doctor.setUser(doctorUser);
        doctor.setDepartment(department);

        patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setMrn("MRN-2026-000001");
        patient.setFirstName("Sunil");
        patient.setLastName("Perera");
        patient.setEmail("sunil@example.com");
        patient.setPhone("+94771234567");
    }

    @Test
    @DisplayName("Reminder Job dispatches email and SMS for upcoming appointments and updates status to SENT")
    void testReminderJobExecution() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        Appointment apt = new Appointment();
        apt.setId(UUID.randomUUID());
        apt.setAppointmentNumber("APT-20261101-0001");
        apt.setDoctor(doctor);
        apt.setPatient(patient);
        apt.setDepartment(department);
        apt.setAppointmentDate(tomorrow);
        apt.setStartTime(LocalTime.of(10, 0));
        apt.setEndTime(LocalTime.of(10, 20));
        apt.setTokenNumber(1);
        apt.setStatus(AppointmentStatus.CONFIRMED);
        apt.setReminderStatus(ReminderStatus.PENDING);

        when(appointmentRepository.findByAppointmentDateAndReminderStatusAndStatusIn(eq(tomorrow), eq(ReminderStatus.PENDING), any()))
                .thenReturn(List.of(apt));
        when(mailService.sendReminderEmail(apt)).thenReturn(true);
        when(smsService.sendSms(eq("+94771234567"), anyString())).thenReturn(true);

        int sent = reminderJob.processRemindersForDate(tomorrow);

        assertThat(sent).isEqualTo(1);
        assertThat(apt.getReminderStatus()).isEqualTo(ReminderStatus.SENT);
        assertThat(apt.getReminderSentAt()).isNotNull();

        verify(mailService, times(1)).sendReminderEmail(apt);
        verify(smsService, times(1)).sendSms(eq("+94771234567"), anyString());
        verify(appointmentRepository, times(1)).save(apt);
    }

    @Test
    @DisplayName("No-Show Job transitions expired past and un-checked-in appointments to NO_SHOW with audit history")
    void testNoShowJobExecution() {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.of(15, 0);

        // Appointment 1: yesterday, un-checked-in
        Appointment pastApt = new Appointment();
        pastApt.setId(UUID.randomUUID());
        pastApt.setAppointmentNumber("APT-20261030-0001");
        pastApt.setAppointmentDate(today.minusDays(1));
        pastApt.setStartTime(LocalTime.of(9, 0));
        pastApt.setEndTime(LocalTime.of(9, 20));
        pastApt.setStatus(AppointmentStatus.SCHEDULED);

        // Appointment 2: today, slot ended at 14:00 (more than 30 mins ago)
        Appointment todayExpiredApt = new Appointment();
        todayExpiredApt.setId(UUID.randomUUID());
        todayExpiredApt.setAppointmentNumber("APT-20261031-0002");
        todayExpiredApt.setAppointmentDate(today);
        todayExpiredApt.setStartTime(LocalTime.of(13, 40));
        todayExpiredApt.setEndTime(LocalTime.of(14, 0));
        todayExpiredApt.setStatus(AppointmentStatus.CONFIRMED);

        when(appointmentRepository.findByAppointmentDateLessThanEqualAndStatusIn(eq(today), any()))
                .thenReturn(List.of(pastApt, todayExpiredApt));

        int updated = noShowJob.processNoShows(today, now);

        assertThat(updated).isEqualTo(2);
        assertThat(pastApt.getStatus()).isEqualTo(AppointmentStatus.NO_SHOW);
        assertThat(todayExpiredApt.getStatus()).isEqualTo(AppointmentStatus.NO_SHOW);

        verify(appointmentRepository, times(2)).save(any(Appointment.class));
        verify(historyRepository, times(2)).save(any(AppointmentStatusHistory.class));
    }
}

