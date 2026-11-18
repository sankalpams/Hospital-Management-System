package lk.hms.modules.doctors.repository;

import lk.hms.modules.doctors.entity.DoctorSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScheduleRepository extends JpaRepository<DoctorSchedule, Long> {

    List<DoctorSchedule> findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(Long doctorId);

    List<DoctorSchedule> findByDoctorIdAndDayOfWeekAndActiveTrue(Long doctorId, int dayOfWeek);
}
