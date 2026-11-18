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
@Table(name = "lab_tests")
@Getter
@Setter
@NoArgsConstructor
public class LabTest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(name = "sample_type", nullable = false, length = 20)
    private String sampleType;

    @Column(name = "result_type", nullable = false, length = 10)
    private String resultType;

    @Column(length = 20)
    private String unit;

    @Column(name = "ref_range_low", precision = 12, scale = 3)
    private BigDecimal refRangeLow;

    @Column(name = "ref_range_high", precision = 12, scale = 3)
    private BigDecimal refRangeHigh;

    @Column(name = "ref_range_text", length = 120)
    private String refRangeText;

    @Column(name = "turnaround_hours", nullable = false, columnDefinition = "smallint")
    private int turnaroundHours;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
