package lk.hms.modules.pharmacy.repository;

import lk.hms.modules.pharmacy.entity.MedicineCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MedicineCategoryRepository extends JpaRepository<MedicineCategory, Long> {

    List<MedicineCategory> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
