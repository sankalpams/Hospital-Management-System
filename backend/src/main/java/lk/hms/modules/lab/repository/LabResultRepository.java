package lk.hms.modules.lab.repository;

import lk.hms.modules.lab.entity.LabResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LabResultRepository extends JpaRepository<LabResult, Long> {

    Optional<LabResult> findByLabRequestItemId(Long itemId);

    List<LabResult> findByLabRequestItemIdIn(Collection<Long> itemIds);
}
