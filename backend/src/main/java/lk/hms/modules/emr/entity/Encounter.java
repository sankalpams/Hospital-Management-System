package lk.hms.modules.emr.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;


/** One clinical contact (OPD visit, admission, emergency). All EMR entries hang off an encounter. */
@Entity
@Table(name = "encounters")
@Getter
@Setter
@NoArgsConstructor
public class Encounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public enum Type { OPD, INPATIENT, EMERGENCY }

    public enum Status { OPEN, CLOSED }

    @Column(name = "encounter_no", nullable = false, unique = true, length = 20, updatable = false)
    private String encounterNo;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    @Column(name = "department_id", nullable = false)
    private Long departmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Type type;

    @Column(name = "appointment_id")
    private Long appointmentId;

    @Column(name = "admission_id")
    private Long admissionId;

    @Column(name = "chief_complaint", length = 500)
    private String chiefComplaint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Status status = Status.OPEN;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Version
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
