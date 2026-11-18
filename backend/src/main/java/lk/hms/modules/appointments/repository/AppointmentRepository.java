package lk.hms.modules.appointments.repository;

import lk.hms.modules.appointments.entity.Appointment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    @Query("""
            select a from Appointment a join fetch a.patient join fetch a.doctor d join fetch d.employee
            join fetch a.department where a.id = :id
            """)
    Optional<Appointment> findDetailed(Long id);

    /** All filters optional; times are UTC bounds [from, to). */
    @Query(value = """
            select a from Appointment a join fetch a.patient p join fetch a.doctor d join fetch d.employee e
            join fetch a.department dep
            where a.slotStart >= :from and a.slotStart < :to
              and (:doctorId is null or d.id = :doctorId)
              and (:departmentId is null or dep.id = :departmentId)
              and (:patientId is null or p.id = :patientId)
              and a.status in :statuses
              and (:q is null or p.mrn = :exact or a.appointmentNo = :exact
                   or lower(concat(p.firstName, ' ', p.lastName)) like :q)
            """,
            countQuery = """
            select count(a) from Appointment a join a.patient p join a.doctor d join a.department dep
            where a.slotStart >= :from and a.slotStart < :to
              and (:doctorId is null or d.id = :doctorId)
              and (:departmentId is null or dep.id = :departmentId)
              and (:patientId is null or p.id = :patientId)
              and a.status in :statuses
              and (:q is null or p.mrn = :exact or a.appointmentNo = :exact
                   or lower(concat(p.firstName, ' ', p.lastName)) like :q)
            """)
    Page<Appointment> search(LocalDateTime from, LocalDateTime to, Long doctorId, Long departmentId, Long patientId,
                             Collection<Appointment.Status> statuses, String q, String exact, Pageable pageable);

    /** Live appointments of one doctor within [from, to) — used to mark taken slots and check capacity. */
    @Query("""
            select a from Appointment a where a.doctor.id = :doctorId and a.slotStart >= :from and a.slotStart < :to
              and a.status not in (lk.hms.modules.appointments.entity.Appointment.Status.CANCELLED,
                                   lk.hms.modules.appointments.entity.Appointment.Status.NO_SHOW)
            """)
    List<Appointment> findLiveForDoctor(Long doctorId, LocalDateTime from, LocalDateTime to);

    /** Any live appointment of the patient overlapping [start, end) — a patient cannot be in two places at once. */
    @Query("""
            select a from Appointment a where a.patient.id = :patientId and a.slotStart < :end and a.slotEnd > :start
              and a.status not in (lk.hms.modules.appointments.entity.Appointment.Status.CANCELLED,
                                   lk.hms.modules.appointments.entity.Appointment.Status.NO_SHOW,
                                   lk.hms.modules.appointments.entity.Appointment.Status.COMPLETED)
              and (:excludeId is null or a.id <> :excludeId)
            """)
    List<Appointment> findPatientOverlaps(Long patientId, LocalDateTime start, LocalDateTime end, Long excludeId);

    @Query("select coalesce(max(a.tokenNo), 0) from Appointment a where a.doctor.id = :doctorId and a.slotStart >= :from and a.slotStart < :to")
    int maxToken(Long doctorId, LocalDateTime from, LocalDateTime to);

    /**
     * Appointment counts per hospital-local day and status for the month calendar. slot_start is
     * UTC; offsetMinutes shifts it to local time (Asia/Colombo has no DST, so one offset is exact).
     * Rows: [local_date, status, count].
     */
    @Query(value = """
            SELECT DATE(DATE_ADD(slot_start, INTERVAL :offsetMinutes MINUTE)) AS local_day, status, COUNT(*) AS n
            FROM appointments
            WHERE slot_start >= :from AND slot_start < :to
              AND (:doctorId IS NULL OR doctor_id = :doctorId)
              AND (:departmentId IS NULL OR department_id = :departmentId)
            GROUP BY local_day, status
            ORDER BY local_day
            """, nativeQuery = true)
    List<Object[]> countByLocalDay(LocalDateTime from, LocalDateTime to, Long doctorId, Long departmentId, int offsetMinutes);
}
