package lk.hms.modules.doctors.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lk.hms.modules.staff.entity.Employee;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "doctors")
@Getter
@Setter
@NoArgsConstructor
public class Doctor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", unique = true)
    private Employee employee;

    @Column(name = "registration_no", nullable = false, unique = true, length = 30)
    private String registrationNo;

    @Column(nullable = false, length = 100)
    private String specialization;

    private String qualifications;

    @Column(name = "consultation_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal consultationFee;

    @Column(name = "accepting_patients", nullable = false)
    private boolean acceptingPatients = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Bookable = employed, active and accepting new patients. */
    public boolean isBookable() {
        return acceptingPatients && employee.getStatus() == Employee.Status.ACTIVE;
    }

    public String getDisplayName() {
        return "Dr. " + employee.getFullName();
    }
}
