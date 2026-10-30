package com.hms.patient;

import com.hms.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "consents")
@Getter
@Setter
@NoArgsConstructor
public class Consent extends BaseEntity {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(nullable = false, length = 255)
    private String purpose;

    @Column(name = "given_date", nullable = false)
    private LocalDate givenDate;

    @Column(name = "withdrawn_date")
    private LocalDate withdrawnDate;

    @Column(name = "witness_name", length = 100)
    private String witnessName;

    @Column(columnDefinition = "TEXT")
    private String notes;

    public Consent(Patient patient, String purpose, LocalDate givenDate, String witnessName, String notes) {
        this.patient = patient;
        this.purpose = purpose;
        this.givenDate = givenDate;
        this.witnessName = witnessName;
        this.notes = notes;
    }
}

