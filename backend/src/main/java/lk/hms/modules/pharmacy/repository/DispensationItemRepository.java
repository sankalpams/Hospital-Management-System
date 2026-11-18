package lk.hms.modules.pharmacy.repository;

import lk.hms.modules.pharmacy.entity.DispensationItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispensationItemRepository extends JpaRepository<DispensationItem, Long> {
}
