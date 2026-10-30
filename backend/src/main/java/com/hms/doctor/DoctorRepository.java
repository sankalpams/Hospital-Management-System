package com.hms.doctor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DoctorRepository extends JpaRepository<Doctor, UUID> {
    Optional<Doctor> findByUserId(UUID userId);
    Optional<Doctor> findBySlmcRegNumberIgnoreCase(String slmcRegNumber);
    boolean existsBySlmcRegNumberIgnoreCase(String slmcRegNumber);
    boolean existsByUserId(UUID userId);

    @Query("SELECT d FROM Doctor d JOIN d.user u JOIN d.department dept " +
           "WHERE (:q IS NULL OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(d.specialization) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(d.slmcRegNumber) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "AND (:departmentId IS NULL OR dept.id = :departmentId) " +
           "AND (:active IS NULL OR d.active = :active)")
    Page<Doctor> search(String q, Long departmentId, Boolean active, Pageable pageable);

    @Query("SELECT d FROM Doctor d JOIN FETCH d.user u JOIN FETCH d.department dept WHERE d.active = true ORDER BY u.lastName ASC")
    List<Doctor> findAllActive();
}

