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
@Table(name = "dispensation_items")
@Getter
@Setter
@NoArgsConstructor
public class DispensationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dispensation_id", nullable = false)
    private Long dispensationId;

    @Column(name = "prescription_item_id", nullable = false)
    private Long prescriptionItemId;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;
}
