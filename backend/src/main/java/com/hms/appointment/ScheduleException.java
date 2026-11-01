package com.hms.appointment;

import com.hms.common.BaseEntity;
import com.hms.doctor.Doctor;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "schedule_exceptions")
@Getter
@Setter
@NoArgsConstructor
public class ScheduleException extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Enumerated(EnumType.STRING)
    @Column(name = "exception_type", nullable = false, length = 20)
    private ScheduleExceptionType exceptionType;

    @Column(name = "start_date_time", nullable = false)
    private OffsetDateTime startDateTime;

    @Column(name = "end_date_time", nullable = false)
    private OffsetDateTime endDateTime;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "slot_duration_minutes")
    private Integer slotDurationMinutes;

    @Column(name = "max_patients")
    private Integer maxPatients;

    public ScheduleException(Doctor doctor, ScheduleExceptionType exceptionType,
                             OffsetDateTime startDateTime, OffsetDateTime endDateTime,
                             String reason, Integer slotDurationMinutes, Integer maxPatients) {
        this.doctor = doctor;
        this.exceptionType = exceptionType;
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
        this.reason = reason;
        this.slotDurationMinutes = slotDurationMinutes;
        this.maxPatients = maxPatients;
    }
}

