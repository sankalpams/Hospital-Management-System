package lk.hms.modules.patients.controller;

import jakarta.validation.Valid;
import lk.hms.common.api.PageResponse;
import lk.hms.modules.patients.dto.PatientDtos.DocumentResponse;
import lk.hms.modules.patients.dto.PatientDtos.DuplicateCandidate;
import lk.hms.modules.patients.dto.PatientDtos.DuplicateCheckRequest;
import lk.hms.modules.patients.dto.PatientDtos.PatientDetail;
import lk.hms.modules.patients.dto.PatientDtos.PatientRequest;
import lk.hms.modules.patients.dto.PatientDtos.PatientSummary;
import lk.hms.modules.patients.dto.PatientDtos.StatusRequest;
import lk.hms.modules.patients.service.PatientDocumentService;
import lk.hms.modules.patients.service.PatientService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class PatientController {

    private final PatientService patients;
    private final PatientDocumentService documents;

    public PatientController(PatientService patients, PatientDocumentService documents) {
        this.patients = patients;
        this.documents = documents;
    }

    @GetMapping("/patients")
    @PreAuthorize("hasAuthority('patient:read')")
    public PageResponse<PatientSummary> search(@RequestParam(required = false) String q,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               @RequestParam(required = false) String sort) {
        return patients.search(q, status, page, size, sort);
    }

    @GetMapping("/patients/{id}")
    @PreAuthorize("hasAuthority('patient:read')")
    public PatientDetail get(@PathVariable Long id) {
        return patients.get(id);
    }

    @PostMapping("/patients/duplicate-check")
    @PreAuthorize("hasAnyAuthority('patient:create','patient:update')")
    public List<DuplicateCandidate> duplicateCheck(@Valid @RequestBody DuplicateCheckRequest body) {
        return patients.duplicateCheck(body);
    }

    @PostMapping("/patients")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('patient:create')")
    public PatientDetail register(@Valid @RequestBody PatientRequest body) {
        return patients.register(body);
    }

    @PutMapping("/patients/{id}")
    @PreAuthorize("hasAuthority('patient:update')")
    public PatientDetail update(@PathVariable Long id, @Valid @RequestBody PatientRequest body) {
        return patients.update(id, body);
    }

    @PostMapping("/patients/{id}/status")
    @PreAuthorize("hasAuthority('patient:update')")
    public PatientDetail status(@PathVariable Long id, @Valid @RequestBody StatusRequest body) {
        return patients.changeStatus(id, body);
    }

    @GetMapping("/patients/{id}/documents")
    @PreAuthorize("hasAuthority('patient:document:read')")
    public List<DocumentResponse> documents(@PathVariable Long id) {
        return documents.list(id);
    }

    @PostMapping(value = "/patients/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('patient:document:upload')")
    public DocumentResponse upload(@PathVariable Long id, @RequestParam String category, @RequestParam String title,
                                   @RequestPart("file") MultipartFile file) {
        return documents.upload(id, category, title, file);
    }

    /** Always served as an attachment with the server-detected type and nosniff (never rendered inline). */
    @GetMapping("/documents/{documentId}/download")
    @PreAuthorize("hasAuthority('patient:document:read')")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long documentId) {
        PatientDocumentService.Download d = documents.open(documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.document().getContentType()))
                .contentLength(d.document().getSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(d.document().getOriginalName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new InputStreamResource(d.stream()));
    }
}
