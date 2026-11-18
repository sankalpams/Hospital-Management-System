package lk.hms.modules.emr.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;


@Entity
@Table(name = "diagnoses")
@Getter
@Setter
@NoArgsConstructor
public class Diagnosis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "icd10_code", length = 10)
    private String icd10Code;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, length = 10)
    private String type;          // PRIMARY, SECONDARY

    @Column(nullable = false, length = 12)
    private String certainty;     // PROVISIONAL, CONFIRMED, RULED_OUT

    @Column(name = "diagnosed_by", nullable = false)
    private Long diagnosedBy;     // doctors.id

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
