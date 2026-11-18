package lk.hms.modules.staff.repository;

import lk.hms.modules.staff.entity.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {
}
