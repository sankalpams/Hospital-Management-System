package lk.hms.modules.lab.repository;

import lk.hms.modules.lab.entity.LabTest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LabTestRepository extends JpaRepository<LabTest, Long> {

    List<LabTest> findAllByOrderByCategoryAscNameAsc();

    boolean existsByCode(String code);
}
