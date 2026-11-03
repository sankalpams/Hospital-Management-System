package com.hms.emr;

import com.hms.common.BaseEntity;
import com.hms.pharmacy.Medicine;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "prescription_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrescriptionItem extends BaseEntity {

    @jakarta.persistence.Id
    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prescription_id", nullable = false)
    private Prescription prescription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medicine_id")
    private Medicine medicine;

    @Column(name = "quantity_prescribed", nullable = false)
    @Builder.Default
    private Integer quantityPrescribed = 1;

    @Column(name = "drug_name", nullable = false)
    private String drugName;

    @Column(nullable = false, length = 64)
    private String dosage;

    @Column(nullable = false, length = 64)
    private String frequency;

    @Column(nullable = false, length = 64)
    private String duration;

    @Column(nullable = false, length = 64)
    @Builder.Default
    private String route = "Oral";

    @Column(columnDefinition = "TEXT")
    private String instructions;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;
}
