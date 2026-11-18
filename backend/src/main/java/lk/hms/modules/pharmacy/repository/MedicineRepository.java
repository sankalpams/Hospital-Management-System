package lk.hms.modules.pharmacy.repository;

import lk.hms.modules.pharmacy.entity.Medicine;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MedicineRepository extends JpaRepository<Medicine, Long> {

    @Query("""
            select m from Medicine m where m.active = true
              and (lower(m.genericName) like :q or lower(m.brandName) like :q or lower(m.code) like :q)
            order by m.genericName, m.strength
            """)
    List<Medicine> searchActive(String q, Pageable pageable);

    @Query("""
            select m from Medicine m
            where (:q is null or lower(m.genericName) like :q or lower(m.brandName) like :q or lower(m.code) like :q)
              and (:categoryId is null or m.categoryId = :categoryId)
            """)
    org.springframework.data.domain.Page<Medicine> search(String q, Long categoryId, Pageable pageable);

    boolean existsByCode(String code);

    List<Medicine> findByActiveTrue();
}
