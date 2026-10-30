package com.hms.employee;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
    Optional<Employee> findByEmployeeNumberIgnoreCase(String employeeNumber);
    Optional<Employee> findByUserId(UUID userId);
    boolean existsByEmployeeNumberIgnoreCase(String employeeNumber);
    boolean existsByUserId(UUID userId);

    @Query("SELECT e FROM Employee e LEFT JOIN e.user u LEFT JOIN e.department dept " +
           "WHERE (:q IS NULL OR LOWER(e.employeeNumber) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(e.designation) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR (u IS NOT NULL AND (LOWER(u.firstName) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :q, '%'))))) " +
           "AND (:departmentId IS NULL OR (dept IS NOT NULL AND dept.id = :departmentId)) " +
           "AND (:active IS NULL OR e.active = :active)")
    Page<Employee> search(String q, Long departmentId, Boolean active, Pageable pageable);
}

