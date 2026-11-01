package com.hms.appointment;

import com.hms.common.BaseEntity;
import com.hms.department.Department;
import com.hms.doctor.Doctor;
import com.hms.patient.Patient;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "appointments")
@Getter
@Setter
@NoArgsConstructor
public class Appointment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "appointment_number", nullable = false, unique = true, length = 32)
    private String appointmentNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "token_number", nullable = false)
    private Integer tokenNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AppointmentStatus status = AppointmentStatus.SCHEDULED;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private AppointmentType type = AppointmentType.STANDARD;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rescheduled_from_id")
    private Appointment rescheduledFrom;

    @Column(name = "consultation_start_time")
    private OffsetDateTime consultationStartTime;

    @Column(name = "consultation_end_time")
    private OffsetDateTime consultationEndTime;

    @Column(name = "reminder_sent_at")
    private OffsetDateTime reminderSentAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "reminder_status", nullable = false, length = 20)
    private ReminderStatus reminderStatus = ReminderStatus.PENDING;
}

