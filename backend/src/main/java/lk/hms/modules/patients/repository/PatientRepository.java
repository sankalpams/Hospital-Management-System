package lk.hms.modules.patients.repository;

import lk.hms.modules.patients.entity.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    Optional<Patient> findByNic(String nic);

    Optional<Patient> findByPassportNo(String passportNo);

    /**
     * Front-desk search. {@code exact} matches MRN / NIC / phone exactly (indexed lookups);
     * {@code like} is a lower-cased "%term%" over the full name.
     */
    @Query("""
            select p from Patient p
            where (:exact is null or p.mrn = :exact or p.nic = :exact or p.phone = :exact
                   or lower(concat(p.firstName, ' ', p.lastName)) like :like)
              and (:status is null or p.status = :status)
            """)
    Page<Patient> search(String exact, String like, Patient.Status status, Pageable pageable);

    /** Soft duplicate rule (docs/01 §2B): same name and date of birth, or same name and phone. */
    @Query("""
            select p from Patient p
            where lower(p.firstName) = lower(:firstName) and lower(p.lastName) = lower(:lastName)
              and (p.dateOfBirth = :dob or p.phone = :phone)
              and (:excludeId is null or p.id <> :excludeId)
            """)
    List<Patient> findPossibleDuplicates(String firstName, String lastName, LocalDate dob, String phone, Long excludeId);
}
