package lk.hms.modules.emr.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;


@Entity
@Table(name = "allergies")
@Getter
@Setter
@NoArgsConstructor
public class Allergy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "allergen_type", nullable = false, length = 15)
    private String allergenType;   // DRUG, FOOD, ENVIRONMENT, OTHER

    @Column(nullable = false, length = 120)
    private String allergen;

    private String reaction;

    @Column(nullable = false, length = 10)
    private String severity;       // MILD, MODERATE, SEVERE

    @Column(nullable = false, length = 10)
    private String status = "ACTIVE";

    @Column(name = "recorded_by", nullable = false)
    private Long recordedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
