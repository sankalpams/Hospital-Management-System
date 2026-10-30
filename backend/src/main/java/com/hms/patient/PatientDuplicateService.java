package com.hms.patient;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PatientDuplicateService {
    private final PatientRepository patients;

    public record DuplicateMatch(
            UUID id,
            String mrn,
            String fullName,
            String nic,
            LocalDate dateOfBirth,
            String phone,
            String reason
    ) {}

    @Transactional(readOnly = true)
    public List<DuplicateMatch> checkDuplicates(String nic, String firstName, String lastName, LocalDate dob, String phone, UUID excludePatientId) {
        Map<UUID, DuplicateMatch> matches = new LinkedHashMap<>();

        // 1. Exact NIC check
        if (nic != null && !nic.isBlank()) {
            List<Patient> nicMatches = patients.findByNicExact(nic.trim());
            for (Patient p : nicMatches) {
                if (excludePatientId != null && p.getId().equals(excludePatientId)) continue;
                matches.put(p.getId(), new DuplicateMatch(
                        p.getId(), p.getMrn(), p.fullName(), p.getNic(), p.getDateOfBirth(), p.getPhone(),
                        "Exact National Identity Card (NIC) match"
                ));
            }
        }

        // 2. Fuzzy matches: DOB + (phone or last name)
        if (dob != null) {
            String cleanPhone = phone != null && !phone.isBlank() ? phone.trim() : null;
            String cleanLastName = lastName != null && !lastName.isBlank() ? lastName.trim() : null;

            List<Patient> fuzzy = patients.findFuzzyMatches(dob, cleanPhone, cleanLastName);
            for (Patient p : fuzzy) {
                if (excludePatientId != null && p.getId().equals(excludePatientId)) continue;
                if (!matches.containsKey(p.getId())) {
                    String reason;
                    if (cleanPhone != null && cleanPhone.equals(p.getPhone())) {
                        reason = "Identical date of birth and telephone number";
                    } else {
                        reason = "Identical date of birth and matching family name";
                    }
                    matches.put(p.getId(), new DuplicateMatch(
                            p.getId(), p.getMrn(), p.fullName(), p.getNic(), p.getDateOfBirth(), p.getPhone(), reason
                    ));
                }
            }
        }

        return new ArrayList<>(matches.values());
    }
}

