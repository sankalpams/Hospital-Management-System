package com.hms.patient;

import com.hms.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "patient_allergies")
@Getter
@Setter
@NoArgsConstructor
public class PatientAllergy extends BaseEntity {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(nullable = false, length = 100)
    private String allergen;

    @Column(nullable = false, length = 255)
    private String reaction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AllergySeverity severity;

    @Column(length = 255)
    private String notes;

    public PatientAllergy(Patient patient, String allergen, String reaction, AllergySeverity severity, String notes) {
        this.patient = patient;
        this.allergen = allergen;
        this.reaction = reaction;
        this.severity = severity;
        this.notes = notes;
    }
}

