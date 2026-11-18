package lk.hms.modules.pharmacy.repository;

import lk.hms.modules.pharmacy.entity.Dispensation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispensationRepository extends JpaRepository<Dispensation, Long> {
}
