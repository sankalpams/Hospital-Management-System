package com.hms.patient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hms.audit.AuditAction;
import com.hms.audit.AuditService;
import com.hms.audit.Audited;
import com.hms.common.AppException;
import com.hms.common.PageResponse;
import com.hms.common.PageableFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PatientService {
    private final PatientRepository patients;
    private final PatientAllergyRepository allergies;
    private final ConsentRepository consents;
    private final MrnGeneratorService mrnGenerator;
    private final PatientDuplicateService duplicateService;
    private final PatientMapper mapper;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PageResponse<PatientDtos.PatientSummaryResponse> list(String q, String district, String bloodGroup, Boolean active, int page, int size, String sort) {
        Page<PatientDtos.PatientSummaryResponse> result = patients.search(
                blank(q), blank(district), blank(bloodGroup), active, PageableFactory.of(page, size, sort)
        ).map(mapper::toSummary);
        return PageResponse.from(result);
    }

    @Transactional(readOnly = true)
    public List<PatientDtos.PatientSummaryResponse> quickSearch(String q, int limit) {
        if (q == null || q.trim().length() < 2) return List.of();
        return patients.quickSearch(q.trim(), Math.min(limit, 20)).stream().map(mapper::toSummary).toList();
    }

    @Transactional
    public PatientDtos.PatientResponse get(UUID id) {
        Patient patient = required(id);

        // Every patient record view is written to the audit log
        ObjectNode viewSummary = objectMapper.createObjectNode();
        viewSummary.put("mrn", patient.getMrn());
        viewSummary.put("fullName", patient.fullName());
        viewSummary.put("action", "PATIENT_RECORD_VIEW");
        auditService.record(AuditAction.VIEW, "Patient", patient.getId().toString(), null, viewSummary);

        return mapper.toResponse(patient);
    }

    @Audited(action = AuditAction.CREATE, entityType = "Patient")
    @Transactional
    public PatientDtos.PatientResponse create(PatientDtos.CreatePatientRequest request) {
        String nic = blank(request.nic());
        if (nic != null && !patients.findByNicExact(nic).isEmpty()) {
            throw AppException.conflict("A patient with NIC/Passport " + nic + " is already registered.");
        }

        Patient patient = new Patient();
        patient.setMrn(mrnGenerator.nextMrn());
        patient.setNic(nic);
        patient.setFirstName(request.firstName().trim());
        patient.setLastName(request.lastName().trim());
        patient.setDateOfBirth(request.dateOfBirth());
        patient.setSex(request.sex().toUpperCase());
        patient.setPhone(blank(request.phone()));
        patient.setEmail(blank(request.email()));
        patient.setAddressLine1(blank(request.addressLine1()));
        patient.setAddressLine2(blank(request.addressLine2()));
        patient.setCity(blank(request.city()));
        patient.setDistrict(request.district().trim());
        patient.setGuardianName(blank(request.guardianName()));
        patient.setGuardianRelationship(blank(request.guardianRelationship()));
        patient.setGuardianPhone(blank(request.guardianPhone()));
        patient.setBloodGroup(blank(request.bloodGroup()));
        patient.setChronicConditions(blank(request.chronicConditions()));
        patient.setInsuranceProvider(blank(request.insuranceProvider()));
        patient.setInsurancePolicyNumber(blank(request.insurancePolicyNumber()));
        patient.setActive(true);

        Patient saved = patients.save(patient);

        if (request.allergies() != null && !request.allergies().isEmpty()) {
            for (PatientDtos.CreateAllergyRequest a : request.allergies()) {
                PatientAllergy allergy = new PatientAllergy(saved, a.allergen().trim(), a.reaction().trim(), a.severity(), blank(a.notes()));
                saved.getAllergies().add(allergies.save(allergy));
            }
        }

        return mapper.toResponse(saved);
    }

    @Audited(action = AuditAction.UPDATE, entityType = "Patient", entityIdArgument = 0)
    @Transactional
    public PatientDtos.PatientResponse update(UUID id, PatientDtos.UpdatePatientRequest request) {
        Patient patient = required(id);
        String nic = blank(request.nic());
        if (nic != null && !nic.equalsIgnoreCase(patient.getNic())) {
            List<Patient> existing = patients.findByNicExact(nic);
            if (existing.stream().anyMatch(p -> !p.getId().equals(id))) {
                throw AppException.conflict("Another patient with NIC/Passport " + nic + " already exists.");
            }
        }

        patient.setNic(nic);
        patient.setFirstName(request.firstName().trim());
        patient.setLastName(request.lastName().trim());
        patient.setDateOfBirth(request.dateOfBirth());
        patient.setSex(request.sex().toUpperCase());
        patient.setPhone(blank(request.phone()));
        patient.setEmail(blank(request.email()));
        patient.setAddressLine1(blank(request.addressLine1()));
        patient.setAddressLine2(blank(request.addressLine2()));
        patient.setCity(blank(request.city()));
        patient.setDistrict(request.district().trim());
        patient.setGuardianName(blank(request.guardianName()));
        patient.setGuardianRelationship(blank(request.guardianRelationship()));
        patient.setGuardianPhone(blank(request.guardianPhone()));
        patient.setBloodGroup(blank(request.bloodGroup()));
        patient.setChronicConditions(blank(request.chronicConditions()));
        patient.setInsuranceProvider(blank(request.insuranceProvider()));
        patient.setInsurancePolicyNumber(blank(request.insurancePolicyNumber()));
        if (request.active() != null) {
            patient.setActive(request.active());
        }

        return mapper.toResponse(patient);
    }

    @Transactional(readOnly = true)
    public List<PatientDuplicateService.DuplicateMatch> checkDuplicates(PatientDtos.CheckDuplicateRequest request) {
        return duplicateService.checkDuplicates(
                request.nic(),
                request.firstName(),
                request.lastName(),
                request.dateOfBirth(),
                request.phone(),
                request.excludePatientId()
        );
    }

    @Audited(action = AuditAction.UPDATE, entityType = "Patient", entityIdArgument = 0)
    @Transactional
    public PatientDtos.PatientResponse merge(UUID sourcePatientId, UUID targetPatientId, String reason) {
        if (sourcePatientId.equals(targetPatientId)) {
            throw AppException.business("A patient cannot be merged into themselves.");
        }

        Patient source = required(sourcePatientId);
        Patient target = required(targetPatientId);

        if (source.getMergedIntoId() != null) {
            throw AppException.business("Source patient " + source.getMrn() + " has already been merged.");
        }

        // Relink allergies
        for (PatientAllergy allergy : source.getAllergies()) {
            allergy.setPatient(target);
            target.getAllergies().add(allergy);
        }
        source.getAllergies().clear();

        // Relink documents
        for (PatientDocument doc : source.getDocuments()) {
            doc.setPatient(target);
            target.getDocuments().add(doc);
        }
        source.getDocuments().clear();

        // Relink consents
        for (Consent consent : source.getConsents()) {
            consent.setPatient(target);
            target.getConsents().add(consent);
        }
        source.getConsents().clear();

        source.setActive(false);
        source.setMergedIntoId(target.getId());
        source.setDeletedAt(Instant.now());

        ObjectNode auditInfo = objectMapper.createObjectNode();
        auditInfo.put("action", "PATIENT_MERGE");
        auditInfo.put("sourceMrn", source.getMrn());
        auditInfo.put("targetMrn", target.getMrn());
        auditInfo.put("reason", reason != null ? reason : "Administrative patient consolidation");
        auditService.record(AuditAction.UPDATE, "Patient", target.getId().toString(), null, auditInfo);

        return mapper.toResponse(target);
    }

    @Audited(action = AuditAction.DELETE, entityType = "Patient", entityIdArgument = 0)
    @Transactional
    public void delete(UUID id) {
        Patient patient = required(id);
        patient.setActive(false);
        patient.setDeletedAt(Instant.now());
    }

    @Transactional
    public PatientDtos.AllergyResponse addAllergy(UUID patientId, PatientDtos.CreateAllergyRequest request) {
        Patient patient = required(patientId);
        PatientAllergy allergy = new PatientAllergy(
                patient,
                request.allergen().trim(),
                request.reaction().trim(),
                request.severity(),
                blank(request.notes())
        );
        PatientAllergy saved = allergies.save(allergy);
        patient.getAllergies().add(saved);
        auditService.record(AuditAction.CREATE, "PatientAllergy", saved.getId().toString(), null, null);
        return mapper.toAllergyResponse(saved);
    }

    @Transactional
    public void removeAllergy(UUID patientId, UUID allergyId) {
        Patient patient = required(patientId);
        patient.getAllergies().removeIf(a -> a.getId().equals(allergyId));
        allergies.deleteById(allergyId);
        auditService.record(AuditAction.DELETE, "PatientAllergy", allergyId.toString(), null, null);
    }

    @Transactional
    public PatientDtos.ConsentResponse addConsent(UUID patientId, PatientDtos.CreateConsentRequest request) {
        Patient patient = required(patientId);
        Consent consent = new Consent(
                patient,
                request.purpose().trim(),
                request.givenDate(),
                blank(request.witnessName()),
                blank(request.notes())
        );
        Consent saved = consents.save(consent);
        patient.getConsents().add(saved);
        auditService.record(AuditAction.CREATE, "Consent", saved.getId().toString(), null, null);
        return mapper.toConsentResponse(saved);
    }

    @Transactional
    public PatientDtos.ConsentResponse withdrawConsent(UUID patientId, UUID consentId) {
        Consent consent = consents.findByIdAndPatientId(consentId, patientId)
                .orElseThrow(() -> AppException.notFound("Consent record not found."));
        consent.setWithdrawnDate(LocalDate.now());
        auditService.record(AuditAction.UPDATE, "Consent", consent.getId().toString(), null, null);
        return mapper.toConsentResponse(consent);
    }

    public Patient required(UUID id) {
        return patients.findById(id)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> AppException.notFound("Patient record not found."));
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

