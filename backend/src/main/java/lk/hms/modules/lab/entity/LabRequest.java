package lk.hms.modules.lab.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "lab_requests")
@Getter
@Setter
@NoArgsConstructor
public class LabRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_no", nullable = false, unique = true, length = 20, updatable = false)
    private String requestNo;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "encounter_id")
    private Long encounterId;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;      // doctors.id

    @Column(name = "department_id", nullable = false)
    private Long departmentId;

    @Column(nullable = false, length = 10)
    private String priority;       // ROUTINE, URGENT, STAT

    @Column(name = "clinical_notes", length = 500)
    private String clinicalNotes;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<LabRequestItem> items = new ArrayList<>();

    @Version
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
