package com.hms.emr;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DrugInteractionRepository extends JpaRepository<DrugInteraction, UUID> {

    @Query("SELECT d FROM DrugInteraction d WHERE " +
            "(LOWER(d.drugA) = LOWER(:drug1) AND LOWER(d.drugB) = LOWER(:drug2)) OR " +
            "(LOWER(d.drugA) = LOWER(:drug2) AND LOWER(d.drugB) = LOWER(:drug1))")
    List<DrugInteraction> findInteractions(@Param("drug1") String drug1, @Param("drug2") String drug2);
}
