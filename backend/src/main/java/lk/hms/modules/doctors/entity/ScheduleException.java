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

/** Dates on which the doctor holds no sessions (leave, conference, blocked). */
@Entity
@Table(name = "doctor_schedule_exceptions")
@Getter
@Setter
@NoArgsConstructor
public class ScheduleException {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    @Column(name = "date_from", nullable = false)
    private LocalDate dateFrom;

    @Column(name = "date_to", nullable = false)
    private LocalDate dateTo;

    @Column(nullable = false, length = 200)
    private String reason;

    public boolean covers(LocalDate date) {
        return !date.isBefore(dateFrom) && !date.isAfter(dateTo);
    }
}
