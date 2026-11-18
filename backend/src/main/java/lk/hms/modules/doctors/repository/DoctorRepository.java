package lk.hms.modules.doctors.repository;

import lk.hms.modules.doctors.entity.Doctor;
import lk.hms.modules.staff.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    boolean existsByRegistrationNo(String registrationNo);

    boolean existsByRegistrationNoAndIdNot(String registrationNo, Long id);

    @Query("select d from Doctor d join fetch d.employee e join fetch e.department where d.id = :id")
    Optional<Doctor> findDetailed(Long id);

    /** Row lock that serialises bookings per doctor (capacity, token numbers). Held until the transaction ends. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Doctor d where d.id = :id")
    Optional<Doctor> lockById(Long id);

    @Query("select d from Doctor d join fetch d.employee e where e.userId = :userId")
    Optional<Doctor> findByUserId(Long userId);

    @Query("""
            select d from Doctor d join d.employee e
            where (:q is null or lower(concat(e.firstName, ' ', e.lastName)) like :q
                   or lower(d.specialization) like :q or lower(d.registrationNo) like :q)
              and (:departmentId is null or e.department.id = :departmentId)
              and (:status is null or e.status = :status)
            """)
    Page<Doctor> search(String q, Long departmentId, Employee.Status status, Pageable pageable);

    @Query("""
            select d from Doctor d join fetch d.employee e join fetch e.department dep
            where e.status = lk.hms.modules.staff.entity.Employee.Status.ACTIVE and d.acceptingPatients = true
              and (:departmentId is null or dep.id = :departmentId)
            order by e.lastName, e.firstName
            """)
    List<Doctor> findBookable(Long departmentId);
}
