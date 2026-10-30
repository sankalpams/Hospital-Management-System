package com.hms.patient;

import com.hms.common.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
public class PatientController {
    private final PatientService patientService;
    private final PatientDocumentService documentService;
    private final PatientExportService exportService;
    private final PatientMapper mapper;

    @GetMapping
    @PreAuthorize("hasAuthority('PATIENT_READ')")
    public PageResponse<PatientDtos.PatientSummaryResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String bloodGroup,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "lastName,asc") String sort
    ) {
        return patientService.list(q, district, bloodGroup, active, page, size, sort);
    }

    @GetMapping("/quick-search")
    @PreAuthorize("hasAuthority('PATIENT_READ')")
    public List<PatientDtos.PatientSummaryResponse> quickSearch(
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return patientService.quickSearch(q, limit);
    }

    @PostMapping("/check-duplicates")
    @PreAuthorize("hasAuthority('PATIENT_READ')")
    public List<PatientDuplicateService.DuplicateMatch> checkDuplicates(
            @Valid @RequestBody PatientDtos.CheckDuplicateRequest request
    ) {
        return patientService.checkDuplicates(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PATIENT_READ')")
    public PatientDtos.PatientResponse get(@PathVariable UUID id) {
        return patientService.get(id);
    }

    @GetMapping("/{id}/clinical-history")
    @PreAuthorize("hasAuthority('EMR_READ')")
    public List<PatientDtos.AllergyResponse> getClinicalHistory(@PathVariable UUID id) {
        // Clinical data requires EMR_READ which RECEPTIONIST does not possess
        return patientService.get(id).allergies();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PATIENT_WRITE')")
    public PatientDtos.PatientResponse create(@Valid @RequestBody PatientDtos.CreatePatientRequest request) {
        return patientService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PATIENT_WRITE')")
    public PatientDtos.PatientResponse update(@PathVariable UUID id, @Valid @RequestBody PatientDtos.UpdatePatientRequest request) {
        return patientService.update(id, request);
    }

    @PostMapping("/{id}/merge")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public PatientDtos.PatientResponse merge(@PathVariable UUID id, @Valid @RequestBody PatientDtos.MergeRequest request) {
        return patientService.merge(id, request.targetPatientId(), request.reason());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public void delete(@PathVariable UUID id) {
        patientService.delete(id);
    }

    @GetMapping("/{id}/export")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<byte[]> export(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "json") String format
    ) {
        PatientExportService.ExportData data = exportService.export(id, format);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(data.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + data.filename() + "\"")
                .body(data.bytes());
    }

    // Document operations
    @GetMapping("/{id}/documents")
    @PreAuthorize("hasAuthority('PATIENT_READ')")
    public List<PatientDtos.DocumentResponse> listDocuments(@PathVariable UUID id) {
        return documentService.listForPatient(id).stream().map(mapper::toDocumentResponse).toList();
    }

    @PostMapping("/{id}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PATIENT_WRITE')")
    public PatientDtos.DocumentResponse uploadDocument(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", defaultValue = "OTHER") DocumentCategory category,
            @RequestParam(value = "notes", required = false) String notes
    ) {
        PatientDocument doc = documentService.upload(id, file, category, notes);
        return mapper.toDocumentResponse(doc);
    }

    @GetMapping("/{id}/documents/{docId}/download")
    @PreAuthorize("hasAuthority('PATIENT_READ')")
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable UUID id,
            @PathVariable UUID docId,
            @RequestParam(defaultValue = "false") boolean preview
    ) {
        PatientDocumentService.DocumentFileDownload download = documentService.download(id, docId);
        String disposition = preview ? "inline" : "attachment";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + download.fileName() + "\"")
                .body(download.resource());
    }

    @DeleteMapping("/{id}/documents/{docId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PATIENT_WRITE')")
    public void deleteDocument(@PathVariable UUID id, @PathVariable UUID docId) {
        documentService.delete(id, docId);
    }

    // Allergy operations
    @PostMapping("/{id}/allergies")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PATIENT_WRITE')")
    public PatientDtos.AllergyResponse addAllergy(@PathVariable UUID id, @Valid @RequestBody PatientDtos.CreateAllergyRequest request) {
        return patientService.addAllergy(id, request);
    }

    @DeleteMapping("/{id}/allergies/{allergyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PATIENT_WRITE')")
    public void removeAllergy(@PathVariable UUID id, @PathVariable UUID allergyId) {
        patientService.removeAllergy(id, allergyId);
    }

    // Consent operations
    @PostMapping("/{id}/consents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PATIENT_WRITE')")
    public PatientDtos.ConsentResponse addConsent(@PathVariable UUID id, @Valid @RequestBody PatientDtos.CreateConsentRequest request) {
        return patientService.addConsent(id, request);
    }

    @PostMapping("/{id}/consents/{consentId}/withdraw")
    @PreAuthorize("hasAuthority('PATIENT_WRITE')")
    public PatientDtos.ConsentResponse withdrawConsent(@PathVariable UUID id, @PathVariable UUID consentId) {
        return patientService.withdrawConsent(id, consentId);
    }
}

