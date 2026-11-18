package lk.hms.modules.pharmacy.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import java.math.BigDecimal;

@Entity
@Table(name = "medicines")
@Getter
@Setter
@NoArgsConstructor
public class Medicine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "generic_name", nullable = false, length = 120)
    private String genericName;

    @Column(name = "brand_name", length = 120)
    private String brandName;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "dosage_form", nullable = false, length = 30)
    private String dosageForm;

    @Column(nullable = false, length = 40)
    private String strength;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "reorder_level", nullable = false)
    private int reorderLevel;

    @Column(name = "prescription_required", nullable = false)
    private boolean prescriptionRequired = true;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public String getDisplayName() {
        return genericName + " " + strength + (brandName == null ? "" : " (" + brandName + ")");
    }
}
