package lk.hms.modules.emr.repository;

import lk.hms.modules.emr.entity.Allergy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AllergyRepository extends JpaRepository<Allergy, Long> {

    List<Allergy> findByPatientIdOrderByStatusAscIdDesc(Long patientId);

    List<Allergy> findByPatientIdAndAllergenTypeAndStatus(Long patientId, String allergenType, String status);

    boolean existsByPatientIdAndAllergenTypeAndAllergenIgnoreCase(Long patientId, String allergenType, String allergen);
}
