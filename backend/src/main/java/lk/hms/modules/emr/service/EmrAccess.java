package lk.hms.modules.emr.service;

import lk.hms.common.audit.AuditService;
import lk.hms.common.exception.BusinessException;
import lk.hms.common.security.CurrentUser;
import lk.hms.common.security.HmsUserPrincipal;
import lk.hms.modules.doctors.entity.Doctor;
import lk.hms.modules.doctors.repository.DoctorRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * Record-level EMR access (docs/05 §3). The permission (emr:read) is checked by the controller;
 * this decides whether the caller has a care relationship with the patient. Without one, a
 * "break-the-glass" reason is required. Every chart view is written to the audit log.
 */
@Component
public class EmrAccess {

    private final DoctorRepository doctors;
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public EmrAccess(DoctorRepository doctors, JdbcTemplate jdbc, AuditService audit) {
        this.doctors = doctors;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public Optional<Doctor> callerDoctor() {
        return doctors.findByUserId(CurrentUser.require().getId());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void checkRead(Long patientId, String reason) {
        if (hasCareRelationship(patientId)) {
            audit.recordChange("VIEW_SENSITIVE", "PATIENT", patientId, "Medical record viewed", null, null);
            return;
        }
        if (reason == null || reason.trim().length() < 10) {
            throw BusinessException.forbidden("BREAK_GLASS_REQUIRED",
                    "You are not treating this patient. State a reason (at least 10 characters) to open the record; it will be audited.");
        }
        audit.recordChange("VIEW_SENSITIVE", "PATIENT", patientId, "Break-the-glass access", null,
                Map.of("reason", reason.trim()));
    }

    /**
     * Doctor: has seen or is booked to see the patient. Nurse: the patient is in the clinic now or admitted.
     * shortcut: nurses see every admitted patient, not only their wards; restrict via nurse_ward_assignments when wards are staffed.
     */
    public boolean hasCareRelationship(Long patientId) {
        HmsUserPrincipal me = CurrentUser.require();
        Optional<Doctor> doctor = callerDoctor();
        if (doctor.isPresent()) {
            Boolean own = jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM encounters WHERE patient_id = ? AND doctor_id = ?)
                        OR EXISTS(SELECT 1 FROM appointments WHERE patient_id = ? AND doctor_id = ? AND status NOT IN ('CANCELLED','NO_SHOW'))
                        OR EXISTS(SELECT 1 FROM admissions WHERE patient_id = ? AND attending_doctor_id = ? AND status = 'ADMITTED')
                    """, Boolean.class, patientId, doctor.get().getId(), patientId, doctor.get().getId(), patientId, doctor.get().getId());
            if (Boolean.TRUE.equals(own)) {
                return true;
            }
        }
        if (me.getRoles().contains("NURSE")) {
            Boolean present = jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM appointments WHERE patient_id = ? AND status IN ('CHECKED_IN','IN_PROGRESS'))
                        OR EXISTS(SELECT 1 FROM admissions WHERE patient_id = ? AND status = 'ADMITTED')
                    """, Boolean.class, patientId, patientId);
            return Boolean.TRUE.equals(present);
        }
        return false;
    }
}
