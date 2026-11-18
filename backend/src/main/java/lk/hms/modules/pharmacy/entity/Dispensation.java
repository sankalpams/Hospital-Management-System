package lk.hms.modules.pharmacy.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "dispensations")
@Getter
@Setter
@NoArgsConstructor
public class Dispensation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dispensation_no", nullable = false, unique = true, length = 20, updatable = false)
    private String dispensationNo;

    @Column(name = "prescription_id", nullable = false)
    private Long prescriptionId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "dispensed_by", nullable = false)
    private Long dispensedBy;

    @Column(name = "dispensed_at", nullable = false)
    private Instant dispensedAt;

    private String notes;
}
