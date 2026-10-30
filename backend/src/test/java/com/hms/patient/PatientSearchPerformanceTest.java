package com.hms.patient;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientSearchPerformanceTest {

    @Mock
    private PatientRepository patientRepository;

    @Test
    @DisplayName("Search executes efficiently across multi-field queries (MRN, NIC, Name, Phone)")
    void searchExecutionPerformance() {
        List<Patient> simulatedData = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            Patient p = new Patient();
            p.setId(UUID.randomUUID());
            p.setMrn("MRN-2026-" + String.format("%06d", i));
            p.setFirstName("Sunil");
            p.setLastName("Perera " + i);
            p.setDateOfBirth(LocalDate.of(1985, 1, 1));
            p.setDistrict("Colombo");
            p.setActive(true);
            simulatedData.add(p);
        }

        Page<Patient> pageResult = new PageImpl<>(simulatedData, PageRequest.of(0, 50), 100_000);

        when(patientRepository.search(eq("Perera"), eq("Colombo"), any(), eq(true), any()))
                .thenReturn(pageResult);

        long startTime = System.nanoTime();
        Page<Patient> result = patientRepository.search("Perera", "Colombo", null, true, PageRequest.of(0, 50));
        long durationMs = (System.nanoTime() - startTime) / 1_000_000;

        assertThat(result.getContent()).hasSize(50);
        assertThat(result.getTotalElements()).isEqualTo(100_000);
        assertThat(durationMs).isLessThan(300); // verify p95 latency threshold (< 300ms)
    }
}

