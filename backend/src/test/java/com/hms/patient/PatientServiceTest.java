package com.hms.patient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.audit.AuditAction;
import com.hms.audit.AuditService;
import com.hms.common.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    @Mock private PatientRepository patients;
    @Mock private PatientAllergyRepository allergies;
    @Mock private ConsentRepository consents;
    @Mock private MrnGeneratorService mrnGenerator;
    @Mock private PatientDuplicateService duplicateService;
    @Mock private PatientMapper mapper;
    @Mock private AuditService auditService;

    private ObjectMapper objectMapper;
    private PatientService patientService;

    private Patient samplePatient;
    private UUID patientId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        patientService = new PatientService(
                patients, allergies, consents, mrnGenerator,
                duplicateService, mapper, auditService, objectMapper
        );

        patientId = UUID.randomUUID();
        samplePatient = new Patient();
        samplePatient.setId(patientId);
        samplePatient.setMrn("MRN-2026-000001");
        samplePatient.setNic("199212345678");
        samplePatient.setFirstName("Kasun");
        samplePatient.setLastName("Silva");
        samplePatient.setDateOfBirth(LocalDate.of(1992, 3, 15));
        samplePatient.setSex("MALE");
        samplePatient.setDistrict("Colombo");
        samplePatient.setActive(true);
        samplePatient.setAllergies(new ArrayList<>());
        samplePatient.setDocuments(new ArrayList<>());
        samplePatient.setConsents(new ArrayList<>());
    }

    @Test
    @DisplayName("Create patient successfully generates MRN, saves patient, and saves allergies")
    void create_Success() {
        PatientDtos.CreatePatientRequest request = new PatientDtos.CreatePatientRequest(
                "199212345678", "Kasun", "Silva", LocalDate.of(1992, 3, 15), "MALE",
                "+94771234567", "kasun@mail.lk", "12 Main St", null, "Colombo", "Colombo",
                "Mrs. Silva", "Spouse", "+94771234567", "O+", "Hypertension", null, null,
                List.of(new PatientDtos.CreateAllergyRequest("Penicillin", "Rash", AllergySeverity.SEVERE, null))
        );

        when(patients.findByNicExact("199212345678")).thenReturn(List.of());
        when(mrnGenerator.nextMrn()).thenReturn("MRN-2026-000001");
        when(patients.save(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));

        PatientDtos.PatientResponse response = new PatientDtos.PatientResponse(
                patientId, "MRN-2026-000001", "199212345678", "Kasun", "Silva", "Kasun Silva",
                LocalDate.of(1992, 3, 15), 34, "34y / MALE", "MALE", "+94771234567", "kasun@mail.lk",
                "12 Main St", null, "Colombo", "Colombo", "Mrs. Silva", "Spouse", "+94771234567",
                "O+", "Hypertension", null, null, true, null, null, 0L, List.of(), List.of(), List.of()
        );
        when(mapper.toResponse(any(Patient.class))).thenReturn(response);

        PatientDtos.PatientResponse result = patientService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.mrn()).isEqualTo("MRN-2026-000001");
        verify(allergies).save(any(PatientAllergy.class));
        verify(patients).save(any(Patient.class));
    }

    @Test
    @DisplayName("Create patient with duplicate NIC throws conflict exception")
    void create_DuplicateNic_ThrowsConflict() {
        PatientDtos.CreatePatientRequest request = new PatientDtos.CreatePatientRequest(
                "199212345678", "Kasun", "Silva", LocalDate.of(1992, 3, 15), "MALE",
                null, null, null, null, null, "Colombo", null, null, null, null, null, null, null, null
        );

        when(patients.findByNicExact("199212345678")).thenReturn(List.of(samplePatient));

        assertThatThrownBy(() -> patientService.create(request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(appEx.getMessage()).contains("already registered");
                });
    }

    @Test
    @DisplayName("Every patient record view writes a VIEW entry to the audit log")
    void get_WritesAuditLogView() {
        when(patients.findById(patientId)).thenReturn(Optional.of(samplePatient));
        when(mapper.toResponse(samplePatient)).thenReturn(new PatientDtos.PatientResponse(
                patientId, "MRN-2026-000001", null, "Kasun", "Silva", "Kasun Silva",
                LocalDate.of(1992, 3, 15), 34, "34y / MALE", "MALE", null, null,
                null, null, null, "Colombo", null, null, null, null, null, null, null,
                true, null, null, 0L, List.of(), List.of(), List.of()
        ));

        patientService.get(patientId);

        verify(auditService).record(
                eq(AuditAction.VIEW),
                eq("Patient"),
                eq(patientId.toString()),
                isNull(),
                any()
        );
    }

    @Test
    @DisplayName("Merge transfers clinical data, deactivates source patient, and records audit trail")
    void merge_TransfersRecordsAndDeactivatesSource() {
        UUID targetId = UUID.randomUUID();
        Patient targetPatient = new Patient();
        targetPatient.setId(targetId);
        targetPatient.setMrn("MRN-2026-000002");
        targetPatient.setFirstName("Kasun");
        targetPatient.setLastName("Silva");
        targetPatient.setDateOfBirth(LocalDate.of(1992, 3, 15));
        targetPatient.setDistrict("Colombo");
        targetPatient.setActive(true);
        targetPatient.setAllergies(new ArrayList<>());
        targetPatient.setDocuments(new ArrayList<>());
        targetPatient.setConsents(new ArrayList<>());

        PatientAllergy allergy = new PatientAllergy(samplePatient, "Aspirin", "Angioedema", AllergySeverity.SEVERE, null);
        samplePatient.getAllergies().add(allergy);

        when(patients.findById(patientId)).thenReturn(Optional.of(samplePatient));
        when(patients.findById(targetId)).thenReturn(Optional.of(targetPatient));
        when(mapper.toResponse(targetPatient)).thenReturn(mock(PatientDtos.PatientResponse.class));

        patientService.merge(patientId, targetId, "Duplicate registration at emergency ward");

        assertThat(samplePatient.isActive()).isFalse();
        assertThat(samplePatient.getMergedIntoId()).isEqualTo(targetId);
        assertThat(samplePatient.getDeletedAt()).isNotNull();

        assertThat(targetPatient.getAllergies()).contains(allergy);
        assertThat(allergy.getPatient()).isEqualTo(targetPatient);

        verify(auditService).record(
                eq(AuditAction.UPDATE),
                eq("Patient"),
                eq(targetId.toString()),
                isNull(),
                any()
        );
    }

    @Test
    @DisplayName("Attempting to merge a patient into themselves throws business validation exception")
    void merge_SelfMerge_ThrowsException() {
        assertThatThrownBy(() -> patientService.merge(patientId, patientId, "Self merge"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("cannot be merged into themselves");
    }
}

