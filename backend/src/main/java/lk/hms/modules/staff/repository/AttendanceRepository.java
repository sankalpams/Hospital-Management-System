package lk.hms.modules.staff.repository;

import lk.hms.modules.staff.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    List<Attendance> findByWorkDate(LocalDate workDate);

    List<Attendance> findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(Long employeeId, LocalDate from, LocalDate to);
}
