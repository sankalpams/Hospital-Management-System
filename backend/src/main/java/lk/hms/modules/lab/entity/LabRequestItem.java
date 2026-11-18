package lk.hms.modules.lab.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "lab_request_items")
@Getter
@Setter
@NoArgsConstructor
public class LabRequestItem {

    public enum Status {
        REQUESTED, SAMPLE_COLLECTED, PROCESSING, COMPLETED, REVIEWED, CANCELLED;

        /** Linear workflow; CANCELLED only from REQUESTED. */
        public boolean canMoveTo(Status next) {
            return next == CANCELLED ? this == REQUESTED : next.ordinal() == ordinal() + 1 && this != CANCELLED;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lab_request_id")
    private LabRequest request;

    @Column(name = "lab_test_id", nullable = false)
    private Long labTestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.REQUESTED;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "sample_barcode", length = 30)
    private String sampleBarcode;

    @Column(name = "sample_collected_at")
    private Instant sampleCollectedAt;

    @Column(name = "sample_collected_by")
    private Long sampleCollectedBy;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;       // doctors.id

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Version
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
