package lk.hms.modules.staff.repository;

import jakarta.persistence.LockModeType;
import lk.hms.modules.staff.entity.LeaveRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LeaveRequest l where l.id = :id")
    Optional<LeaveRequest> lockById(Long id);

    @Query("""
            select count(l) > 0 from LeaveRequest l
            where l.employeeId = :employeeId and l.status in :statuses and l.id <> :excludeId
              and l.startDate <= :end and l.endDate >= :start
            """)
    boolean overlaps(Long employeeId, Collection<LeaveRequest.Status> statuses, LocalDate start, LocalDate end, Long excludeId);

    @Query("""
            select coalesce(sum(l.days), 0) from LeaveRequest l
            where l.employeeId = :employeeId and l.leaveTypeId = :typeId and l.status in :statuses
              and l.startDate between :yearStart and :yearEnd and l.id <> :excludeId
            """)
    BigDecimal daysTaken(Long employeeId, Long typeId, Collection<LeaveRequest.Status> statuses,
                         LocalDate yearStart, LocalDate yearEnd, Long excludeId);

    /** Approved leave covering a day, for the attendance register. */
    @Query("select l from LeaveRequest l where l.status = lk.hms.modules.staff.entity.LeaveRequest.Status.APPROVED and l.startDate <= :day and l.endDate >= :day")
    List<LeaveRequest> approvedOn(LocalDate day);

    @Query("""
            select l from LeaveRequest l
            where (:status is null or l.status = :status) and (:employeeId is null or l.employeeId = :employeeId)
            """)
    Page<LeaveRequest> search(LeaveRequest.Status status, Long employeeId, Pageable pageable);
}
