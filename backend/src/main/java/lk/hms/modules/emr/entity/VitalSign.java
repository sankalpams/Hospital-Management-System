package lk.hms.modules.emr.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.math.BigDecimal;

@Entity
@Table(name = "vital_signs")
@Getter
@Setter
@NoArgsConstructor
public class VitalSign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "encounter_id")
    private Long encounterId;

    @Column(name = "recorded_by", nullable = false)
    private Long recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "temperature_c", precision = 4, scale = 1)
    private BigDecimal temperatureC;

    @Column(name = "pulse_bpm", columnDefinition = "smallint")
    private Integer pulseBpm;

    @Column(name = "systolic_mmhg", columnDefinition = "smallint")
    private Integer systolicMmhg;

    @Column(name = "diastolic_mmhg", columnDefinition = "smallint")
    private Integer diastolicMmhg;

    @Column(name = "respiratory_rate", columnDefinition = "smallint")
    private Integer respiratoryRate;

    @Column(name = "spo2_percent", columnDefinition = "tinyint")
    private Integer spo2Percent;

    @Column(name = "weight_kg", precision = 5, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "height_cm", precision = 5, scale = 1)
    private BigDecimal heightCm;

    @Column(name = "blood_glucose_mmol", precision = 4, scale = 1)
    private BigDecimal bloodGlucoseMmol;

    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
