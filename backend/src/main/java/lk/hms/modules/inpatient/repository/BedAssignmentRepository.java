package lk.hms.modules.inpatient.repository;

import lk.hms.modules.inpatient.entity.BedAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BedAssignmentRepository extends JpaRepository<BedAssignment, Long> {

    Optional<BedAssignment> findByAdmissionIdAndReleasedAtIsNull(Long admissionId);

    List<BedAssignment> findByAdmissionIdOrderByAssignedAtAsc(Long admissionId);

    List<BedAssignment> findByBedIdInAndReleasedAtIsNull(Collection<Long> bedIds);

    List<BedAssignment> findByAdmissionIdIn(Collection<Long> admissionIds);
}
