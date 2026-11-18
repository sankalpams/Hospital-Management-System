package lk.hms.modules.staff.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lk.hms.modules.departments.entity.Department;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Hospital employee. Phase 3 uses it as the base of a doctor profile; full staff management
 * (all categories, attendance, leave) is Phase 4.
 */
@Entity
@Table(name = "employees")
@Getter
@Setter
@NoArgsConstructor
public class Employee {

    public enum Status { ACTIVE, ON_LEAVE, SUSPENDED, RESIGNED, TERMINATED }

    public enum Category { DOCTOR, NURSE, LAB, PHARMACY, ADMIN, FINANCE, SUPPORT }

    public enum EmploymentType { PERMANENT, CONTRACT, VISITING, TRAINEE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_no", nullable = false, unique = true, length = 20, updatable = false)
    private String employeeNo;

    @Column(name = "user_id")
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "first_name", nullable = false, length = 60)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 60)
    private String lastName;

    @Column(length = 12)
    private String nic;

    @Column(nullable = false, length = 10)
    private String gender;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 120)
    private String email;

    private String address;

    @Column(nullable = false, length = 80)
    private String designation;

    @Enumerated(EnumType.STRING)
    @Column(name = "staff_category", nullable = false, length = 20)
    private Category staffCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 20)
    private EmploymentType employmentType;

    @Column(name = "join_date", nullable = false)
    private LocalDate joinDate;

    @Column(name = "leave_date")
    private LocalDate leaveDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Version
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
