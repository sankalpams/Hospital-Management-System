package lk.hms.modules.departments.repository;

import lk.hms.modules.departments.entity.Department;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    boolean existsByCode(String code);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    List<Department> findByActiveTrueOrderByName();

    @Query("""
            select d from Department d
            where (:q is null or lower(d.name) like :q or lower(d.code) like :q)
              and (:type is null or d.type = :type)
              and (:active is null or d.active = :active)
            """)
    Page<Department> search(String q, Department.Type type, Boolean active, Pageable pageable);
}
