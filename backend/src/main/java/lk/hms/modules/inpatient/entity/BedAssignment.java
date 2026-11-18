package lk.hms.modules.inpatient.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/** Bed occupancy history; DB unique keys on the active columns forbid double occupancy. */
@Entity
@Table(name = "bed_assignments")
@Getter
@Setter
@NoArgsConstructor
public class BedAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admission_id", nullable = false)
    private Long admissionId;

    @Column(name = "bed_id", nullable = false)
    private Long bedId;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "release_reason", length = 20)
    private String releaseReason;

    @Column(name = "assigned_by")
    private Long assignedBy;

    @Column(name = "active_bed_id", insertable = false, updatable = false)
    private Long activeBedId;

    @Column(name = "active_admission_id", insertable = false, updatable = false)
    private Long activeAdmissionId;
}
