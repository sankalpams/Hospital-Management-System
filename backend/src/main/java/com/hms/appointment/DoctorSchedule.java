package com.hms.appointment;

import com.hms.common.BaseEntity;
import com.hms.doctor.Doctor;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "doctor_schedules")
@Getter
@Setter
@NoArgsConstructor
public class DoctorSchedule extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "slot_duration_minutes", nullable = false)
    private Integer slotDurationMinutes;

    @Column(name = "max_patients", nullable = false)
    private Integer maxPatients;

    @Column(name = "room_number", length = 50)
    private String roomNumber;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public DoctorSchedule(Doctor doctor, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime,
                          Integer slotDurationMinutes, Integer maxPatients, String roomNumber) {
        this.doctor = doctor;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.slotDurationMinutes = slotDurationMinutes;
        this.maxPatients = maxPatients;
        this.roomNumber = roomNumber;
        this.active = true;
    }
}

