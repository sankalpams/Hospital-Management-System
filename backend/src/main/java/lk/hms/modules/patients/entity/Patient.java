package lk.hms.modules.patients.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "patients")
@Getter
@Setter
@NoArgsConstructor
public class Patient {

    public enum Gender { MALE, FEMALE, OTHER }

    public enum Status { ACTIVE, INACTIVE, DECEASED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20, updatable = false)
    private String mrn;

    @Column(name = "first_name", nullable = false, length = 60)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 60)
    private String lastName;

    @Column(length = 12)
    private String nic;

    @Column(name = "passport_no", length = 20)
    private String passportNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Gender gender;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(name = "blood_group", length = 5)
    private String bloodGroup;

    @Column(name = "marital_status", length = 15)
    private String maritalStatus;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 120)
    private String email;

    @Column(name = "address_line1", length = 150)
    private String addressLine1;

    @Column(name = "address_line2", length = 150)
    private String addressLine2;

    @Column(length = 60)
    private String city;

    @Column(length = 40)
    private String district;

    @Column(length = 80)
    private String occupation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Status status = Status.ACTIVE;

    @Column(name = "deceased_at")
    private LocalDate deceasedAt;

    @Column(name = "registered_by")
    private Long registeredBy;

    @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("primaryContact desc, id asc")
    private List<EmergencyContact> emergencyContacts = new ArrayList<>();

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

    /** Age is derived, never stored (it would go stale). */
    public int ageOn(LocalDate date) {
        return Period.between(dateOfBirth, date).getYears();
    }
}
