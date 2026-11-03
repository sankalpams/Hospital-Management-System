package com.hms.emr;

import com.hms.common.BaseEntity;
import com.hms.patient.Patient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Entity
@Table(name = "vitals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vitals extends BaseEntity {

    @jakarta.persistence.Id
    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "encounter_id", nullable = false)
    private Encounter encounter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "recorded_at", nullable = false)
    @Builder.Default
    private Instant recordedAt = Instant.now();

    @Column(name = "systolic_bp")
    private Integer systolicBp;

    @Column(name = "diastolic_bp")
    private Integer diastolicBp;

    @Column(name = "pulse_rate")
    private Integer pulseRate;

    @Column(name = "temperature_celsius", precision = 4, scale = 1)
    private BigDecimal temperatureCelsius;

    @Column(name = "respiratory_rate")
    private Integer respiratoryRate;

    @Column(name = "spo2", precision = 4, scale = 1)
    private BigDecimal spo2;

    @Column(name = "weight_kg", precision = 5, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "height_cm", precision = 5, scale = 2)
    private BigDecimal heightCm;

    @Column(precision = 4, scale = 1)
    private BigDecimal bmi;

    @Column(name = "is_bp_abnormal")
    @Builder.Default
    private Boolean isBpAbnormal = false;

    @Column(name = "is_pulse_abnormal")
    @Builder.Default
    private Boolean isPulseAbnormal = false;

    @Column(name = "is_temp_abnormal")
    @Builder.Default
    private Boolean isTempAbnormal = false;

    @Column(name = "is_spo2_abnormal")
    @Builder.Default
    private Boolean isSpo2Abnormal = false;

    @Column(name = "is_bmi_abnormal")
    @Builder.Default
    private Boolean isBmiAbnormal = false;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @PrePersist
    @PreUpdate
    public void calculateBmiAndAbnormalFlags() {
        if (recordedAt == null) {
            recordedAt = Instant.now();
        }

        // Calculate BMI: weight_kg / (height_cm / 100)^2
        if (weightKg != null && heightCm != null && heightCm.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal heightM = heightCm.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            BigDecimal heightSquared = heightM.multiply(heightM);
            if (heightSquared.compareTo(BigDecimal.ZERO) > 0) {
                this.bmi = weightKg.divide(heightSquared, 1, RoundingMode.HALF_UP);
            }
        }

        // Blood pressure: Normal 90-120 systolic, 60-80 diastolic
        if (systolicBp != null || diastolicBp != null) {
            boolean sysAbn = systolicBp != null && (systolicBp < 90 || systolicBp > 130);
            boolean diaAbn = diastolicBp != null && (diastolicBp < 60 || diastolicBp > 85);
            this.isBpAbnormal = sysAbn || diaAbn;
        } else {
            this.isBpAbnormal = false;
        }

        // Pulse: Normal 60-100 bpm
        if (pulseRate != null) {
            this.isPulseAbnormal = pulseRate < 60 || pulseRate > 100;
        } else {
            this.isPulseAbnormal = false;
        }

        // Temperature: Normal 36.1 - 37.2 C
        if (temperatureCelsius != null) {
            this.isTempAbnormal = temperatureCelsius.compareTo(BigDecimal.valueOf(36.0)) < 0
                    || temperatureCelsius.compareTo(BigDecimal.valueOf(37.5)) > 0;
        } else {
            this.isTempAbnormal = false;
        }

        // SpO2: Normal >= 95%
        if (spo2 != null) {
            this.isSpo2Abnormal = spo2.compareTo(BigDecimal.valueOf(95.0)) < 0;
        } else {
            this.isSpo2Abnormal = false;
        }

        // BMI: Normal 18.5 - 24.9
        if (bmi != null) {
            this.isBmiAbnormal = bmi.compareTo(BigDecimal.valueOf(18.5)) < 0
                    || bmi.compareTo(BigDecimal.valueOf(25.0)) >= 0;
        } else {
            this.isBmiAbnormal = false;
        }
    }
}
