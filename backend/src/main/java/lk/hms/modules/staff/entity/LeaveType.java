package lk.hms.modules.staff.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Reference data seeded in V9; read-only here. */
@Entity
@Table(name = "leave_types")
@Getter
@NoArgsConstructor
public class LeaveType {

    @Id
    private Long id;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 60)
    private String name;

    /** null = no yearly limit (no-pay leave). */
    @Column(name = "days_per_year", columnDefinition = "smallint")
    private Integer daysPerYear;

    @Column(nullable = false)
    private boolean paid;
}
