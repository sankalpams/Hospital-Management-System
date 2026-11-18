package lk.hms.modules.billing.repository;

import lk.hms.modules.billing.entity.ServiceItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceItemRepository extends JpaRepository<ServiceItem, Long> {

    List<ServiceItem> findByActiveTrueOrderByCategoryAscNameAsc();
}
