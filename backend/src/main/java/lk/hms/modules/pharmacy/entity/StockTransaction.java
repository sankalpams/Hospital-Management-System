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

/** Append-only stock ledger; a batch's quantity on hand always equals the sum of its rows. */
@Entity
@Table(name = "stock_transactions")
@Getter
@Setter
@NoArgsConstructor
public class StockTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "medicine_id", nullable = false)
    private Long medicineId;

    @Column(nullable = false, length = 20)
    private String type;

    /** Signed: positive in, negative out (enforced by a DB CHECK per type). */
    @Column(nullable = false)
    private int quantity;

    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    @Column(name = "reference_type", length = 30)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    private String note;

    @Column(name = "performed_by")
    private Long performedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
