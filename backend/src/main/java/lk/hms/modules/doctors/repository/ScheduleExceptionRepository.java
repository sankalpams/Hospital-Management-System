package lk.hms.modules.doctors.repository;

import lk.hms.modules.doctors.entity.ScheduleException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface ScheduleExceptionRepository extends JpaRepository<ScheduleException, Long> {

    List<ScheduleException> findByDoctorIdAndDateToGreaterThanEqualOrderByDateFrom(Long doctorId, LocalDate from);

    @Query("select count(x) > 0 from ScheduleException x where x.doctorId = :doctorId and :date between x.dateFrom and x.dateTo")
    boolean blocks(Long doctorId, LocalDate date);

    @Query("select x from ScheduleException x where x.doctorId = :doctorId and :date between x.dateFrom and x.dateTo")
    List<ScheduleException> findCovering(Long doctorId, LocalDate date);
}
