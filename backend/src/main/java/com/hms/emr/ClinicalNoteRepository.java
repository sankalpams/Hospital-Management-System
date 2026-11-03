package com.hms.emr;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClinicalNoteRepository extends JpaRepository<ClinicalNote, UUID> {

    List<ClinicalNote> findByEncounterIdAndDeletedFalseOrderByCreatedAtAsc(UUID encounterId);

    List<ClinicalNote> findByParentNoteIdAndDeletedFalseOrderByCreatedAtAsc(UUID parentNoteId);
}
