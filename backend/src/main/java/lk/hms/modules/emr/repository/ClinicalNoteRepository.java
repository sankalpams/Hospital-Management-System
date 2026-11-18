package lk.hms.modules.emr.repository;

import lk.hms.modules.emr.entity.ClinicalNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClinicalNoteRepository extends JpaRepository<ClinicalNote, Long> {

    List<ClinicalNote> findByEncounterIdInOrderByIdAsc(List<Long> encounterIds);
}
