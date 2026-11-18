package lk.hms.modules.doctors.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

/** A recurring weekly consultation session in hospital-local time. */
@Entity
@Table(name = "doctor_schedules")
@Getter
@Setter
@NoArgsConstructor
public class DoctorSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    /** ISO day of week: 1 = Monday ... 7 = Sunday. */
    @Column(name = "day_of_week", nullable = false, columnDefinition = "tinyint")
    private int dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "slot_minutes", nullable = false, columnDefinition = "smallint")
    private int slotMinutes;

    @Column(name = "max_patients", nullable = false, columnDefinition = "smallint")
    private int maxPatients;

    @Column(name = "room_label", length = 40)
    private String roomLabel;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(nullable = false)
    private boolean active = true;

    public boolean appliesOn(LocalDate date) {
        return active && date.getDayOfWeek().getValue() == dayOfWeek
                && !date.isBefore(effectiveFrom) && (effectiveTo == null || !date.isAfter(effectiveTo));
    }
}
