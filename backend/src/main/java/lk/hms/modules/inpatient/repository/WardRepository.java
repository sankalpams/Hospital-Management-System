package lk.hms.modules.inpatient.repository;

import lk.hms.modules.inpatient.entity.Ward;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WardRepository extends JpaRepository<Ward, Long> {

    List<Ward> findAllByOrderByNameAsc();

    boolean existsByCode(String code);
}
