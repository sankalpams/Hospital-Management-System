package com.hms.patient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientDuplicateServiceTest {

    @Mock
    private PatientRepository patientRepository;

    private PatientDuplicateService duplicateService;

    private Patient existingPatient;
    private UUID existingId;

    @BeforeEach
    void setUp() {
        duplicateService = new PatientDuplicateService(patientRepository);

        existingId = UUID.randomUUID();
        existingPatient = new Patient();
        existingPatient.setId(existingId);
        existingPatient.setMrn("MRN-2026-000001");
        existingPatient.setNic("199012345678");
        existingPatient.setFirstName("Sunil");
        existingPatient.setLastName("Perera");
        existingPatient.setDateOfBirth(LocalDate.of(1990, 5, 12));
        existingPatient.setPhone("+94771234567");
    }

    @Test
    @DisplayName("Detects duplicate by exact NIC match")
    void checkDuplicates_ExactNic() {
        when(patientRepository.findByNicExact("199012345678"))
                .thenReturn(List.of(existingPatient));

        List<PatientDuplicateService.DuplicateMatch> matches = duplicateService.checkDuplicates(
                "199012345678", "Sunil", "Perera", LocalDate.of(1990, 5, 12), "+94771234567", null
        );

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).mrn()).isEqualTo("MRN-2026-000001");
        assertThat(matches.get(0).reason()).contains("National Identity Card");
    }

    @Test
    @DisplayName("Detects fuzzy duplicate by matching date of birth and phone number")
    void checkDuplicates_FuzzyDobAndPhone() {
        when(patientRepository.findByNicExact("199599999999"))
                .thenReturn(List.of());
        when(patientRepository.findFuzzyMatches(LocalDate.of(1990, 5, 12), "+94771234567", "Silva"))
                .thenReturn(List.of(existingPatient));

        List<PatientDuplicateService.DuplicateMatch> matches = duplicateService.checkDuplicates(
                "199599999999", "Nimal", "Silva", LocalDate.of(1990, 5, 12), "+94771234567", null
        );

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).mrn()).isEqualTo("MRN-2026-000001");
        assertThat(matches.get(0).reason()).contains("telephone number");
    }

    @Test
    @DisplayName("Excludes current patient ID when checking duplicates during update")
    void checkDuplicates_ExcludesSelf() {
        when(patientRepository.findByNicExact("199012345678"))
                .thenReturn(List.of(existingPatient));

        List<PatientDuplicateService.DuplicateMatch> matches = duplicateService.checkDuplicates(
                "199012345678", "Sunil", "Perera", LocalDate.of(1990, 5, 12), "+94771234567", existingId
        );

        assertThat(matches).isEmpty();
    }
}

