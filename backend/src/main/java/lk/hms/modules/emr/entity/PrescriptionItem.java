package lk.hms.modules.emr.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "prescription_items")
@Getter
@Setter
@NoArgsConstructor
public class PrescriptionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prescription_id")
    private Prescription prescription;

    @Column(name = "medicine_id", nullable = false)
    private Long medicineId;

    @Column(nullable = false, length = 40)
    private String dose;

    @Column(nullable = false, length = 40)
    private String frequency;

    @Column(nullable = false, length = 20)
    private String route;

    @Column(name = "duration_days", nullable = false, columnDefinition = "smallint")
    private int durationDays;

    @Column(name = "quantity_prescribed", nullable = false)
    private int quantityPrescribed;

    @Column(name = "quantity_dispensed", nullable = false)
    private int quantityDispensed;

    private String instructions;
}
