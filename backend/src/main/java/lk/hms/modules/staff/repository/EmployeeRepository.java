package lk.hms.modules.staff.repository;

import lk.hms.modules.staff.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByNic(String nic);

    Optional<Employee> findByUserId(Long userId);

    @Query(value = """
            select e from Employee e join fetch e.department d
            where (:category is null or e.staffCategory = :category)
              and (:departmentId is null or d.id = :departmentId)
              and (:status is null or e.status = :status)
              and (:q is null or e.employeeNo = :exact or e.nic = :exact
                   or lower(concat(e.firstName, ' ', e.lastName)) like :q or lower(e.designation) like :q)
            """,
            countQuery = """
            select count(e) from Employee e
            where (:category is null or e.staffCategory = :category)
              and (:departmentId is null or e.department.id = :departmentId)
              and (:status is null or e.status = :status)
              and (:q is null or e.employeeNo = :exact or e.nic = :exact
                   or lower(concat(e.firstName, ' ', e.lastName)) like :q or lower(e.designation) like :q)
            """)
    Page<Employee> search(Employee.Category category, Long departmentId, Employee.Status status, String q, String exact, Pageable pageable);

    /** Staff expected at work on a day: active or on leave, and already joined. */
    @Query("""
            select e from Employee e join fetch e.department
            where e.status in (lk.hms.modules.staff.entity.Employee.Status.ACTIVE, lk.hms.modules.staff.entity.Employee.Status.ON_LEAVE)
              and e.joinDate <= :day
            order by e.lastName, e.firstName
            """)
    List<Employee> onRoll(LocalDate day);
}
