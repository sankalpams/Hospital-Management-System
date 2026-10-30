package com.hms.department;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    Optional<Department> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByNameIgnoreCase(String name);

    List<Department> findByActiveTrueOrderByNameAsc();

    @Query("SELECT d FROM Department d WHERE (:q IS NULL OR LOWER(d.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(d.code) LIKE LOWER(CONCAT('%', :q, '%'))) AND (:active IS NULL OR d.active = :active)")
    Page<Department> search(String q, Boolean active, Pageable pageable);
}

