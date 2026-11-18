package lk.hms.modules.staff.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

/** One row per employee per day (uk_attendance_employee_day). */
@Entity
@Table(name = "attendance")
@Getter
@Setter
@NoArgsConstructor
public class Attendance {

    public enum Status { PRESENT, ABSENT, HALF_DAY, ON_LEAVE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private Long employeeId;

    @Column(name = "work_date", nullable = false, updatable = false)
    private LocalDate workDate;

    @Column(name = "clock_in")
    private Instant clockIn;

    @Column(name = "clock_out")
    private Instant clockOut;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    private String remarks;

    @Column(name = "recorded_by")
    private Long recordedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
