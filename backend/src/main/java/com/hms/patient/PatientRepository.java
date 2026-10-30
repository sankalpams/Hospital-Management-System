package com.hms.patient;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatientRepository extends JpaRepository<Patient, UUID> {
    Optional<Patient> findByMrn(String mrn);
    Optional<Patient> findByNicIgnoreCase(String nic);

    @Query("SELECT p FROM Patient p WHERE p.deletedAt IS NULL AND (:nic IS NOT NULL AND LOWER(p.nic) = LOWER(:nic))")
    List<Patient> findByNicExact(String nic);

    @Query("SELECT p FROM Patient p WHERE p.deletedAt IS NULL AND p.dateOfBirth = :dob AND (p.phone = :phone OR LOWER(p.lastName) = LOWER(:lastName))")
    List<Patient> findFuzzyMatches(LocalDate dob, String phone, String lastName);

    @Query("SELECT p FROM Patient p WHERE p.deletedAt IS NULL AND " +
           "(:q IS NULL OR LOWER(p.mrn) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR (p.nic IS NOT NULL AND LOWER(p.nic) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "OR (p.phone IS NOT NULL AND p.phone LIKE CONCAT('%', :q, '%')) " +
           "OR LOWER(CONCAT(p.firstName, ' ', p.lastName)) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "AND (:district IS NULL OR p.district = :district) " +
           "AND (:bloodGroup IS NULL OR p.bloodGroup = :bloodGroup) " +
           "AND (:active IS NULL OR p.active = :active)")
    Page<Patient> search(String q, String district, String bloodGroup, Boolean active, Pageable pageable);

    @Query(value = "SELECT * FROM patients WHERE deleted_at IS NULL AND (" +
           "mrn ILIKE CONCAT('%', :q, '%') OR " +
           "nic ILIKE CONCAT('%', :q, '%') OR " +
           "phone ILIKE CONCAT('%', :q, '%') OR " +
           "(first_name || ' ' || last_name) ILIKE CONCAT('%', :q, '%')) " +
           "ORDER BY last_name ASC LIMIT :limit", nativeQuery = true)
    List<Patient> quickSearch(String q, int limit);
}

