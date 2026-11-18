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
@Table(name = "lab_results")
@Getter
@Setter
@NoArgsConstructor
public class LabResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lab_request_item_id", nullable = false, unique = true)
    private Long labRequestItemId;

    @Column(name = "numeric_value", precision = 12, scale = 3)
    private BigDecimal numericValue;

    @Column(name = "text_value", length = 1000)
    private String textValue;

    @Column(length = 20)
    private String unit;

    @Column(name = "ref_range_low", precision = 12, scale = 3)
    private BigDecimal refRangeLow;

    @Column(name = "ref_range_high", precision = 12, scale = 3)
    private BigDecimal refRangeHigh;

    @Column(length = 2)
    private String flag;

    @Column(length = 500)
    private String comments;

    @Column(name = "entered_by", nullable = false)
    private Long enteredBy;

    @Column(name = "entered_at", nullable = false)
    private Instant enteredAt;
}
