package lk.hms.modules.inpatient.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "rooms")
@Getter
@Setter
@NoArgsConstructor
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ward_id", nullable = false)
    private Long wardId;

    @Column(name = "room_no", nullable = false, length = 20)
    private String roomNo;

    @Column(name = "room_type", nullable = false, length = 20)
    private String roomType;

    @Column(name = "daily_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal dailyRate;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
