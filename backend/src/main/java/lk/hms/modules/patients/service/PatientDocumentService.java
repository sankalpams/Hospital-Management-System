package lk.hms.modules.patients.service;

import lk.hms.common.audit.AuditService;
import lk.hms.common.exception.BusinessException;
import lk.hms.common.security.CurrentUser;
import lk.hms.common.time.HospitalTime;
import lk.hms.modules.patients.dto.PatientDtos.DocumentResponse;
import lk.hms.modules.patients.entity.PatientDocument;
import lk.hms.modules.patients.repository.PatientDocumentRepository;
import lk.hms.modules.patients.repository.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PatientDocumentService {

    private final PatientDocumentRepository documents;
    private final PatientRepository patients;
    private final DocumentStorage storage;
    private final AuditService audit;
    private final HospitalTime time;

    public PatientDocumentService(PatientDocumentRepository documents, PatientRepository patients,
                                  DocumentStorage storage, AuditService audit, HospitalTime time) {
        this.documents = documents;
        this.patients = patients;
        this.storage = storage;
        this.audit = audit;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> list(Long patientId) {
        requirePatient(patientId);
        return documents.findByPatientIdOrderByCreatedAtDesc(patientId).stream().map(DocumentResponse::from).toList();
    }

    /**
     * File first, then the row. If the transaction rolls back the file is removed, so the DB
     * never points at a missing file and no orphan file is left behind.
     */
    @Transactional
    public DocumentResponse upload(Long patientId, String categoryRaw, String title, MultipartFile file) {
        requirePatient(patientId);
        PatientDocument.Category category = parseCategory(categoryRaw);
        if (title == null || title.isBlank() || title.length() > 150) {
            throw BusinessException.badRequest("TITLE_REQUIRED", "Give the document a title (up to 150 characters).");
        }
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("FILE_EMPTY", "Choose a file to upload.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw BusinessException.badRequest("FILE_UNREADABLE", "The file could not be read.");
        }
        DocumentStorage.Stored stored = storage.store(bytes);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storage.deleteQuietly(stored.storedName());
                }
            }
        });

        PatientDocument d = new PatientDocument();
        d.setPatientId(patientId);
        d.setCategory(category);
        d.setTitle(title.trim());
        d.setOriginalName(safeName(file.getOriginalFilename(), stored.contentType()));
        d.setStoredName(stored.storedName());
        d.setContentType(stored.contentType());
        d.setSizeBytes(stored.size());
        d.setSha256(stored.sha256());
        d.setUploadedBy(CurrentUser.require().getId());
        d.setCreatedAt(time.now());
        documents.saveAndFlush(d);
        audit.recordChange("UPLOAD", "PATIENT", patientId, "Uploaded document '" + d.getTitle() + "'", null,
                Map.of("documentId", d.getId(), "category", category.name(), "sha256", d.getSha256()));
        return DocumentResponse.from(d);
    }

    public record Download(PatientDocument document, InputStream stream) {
    }

    /** Downloads of clinical documents are audited (who looked at what, and when). */
    @Transactional
    public Download open(Long documentId) {
        PatientDocument d = documents.findById(documentId)
                .orElseThrow(() -> BusinessException.notFound("Document", documentId));
        audit.recordChange("VIEW_SENSITIVE", "PATIENT", d.getPatientId(), "Downloaded document '" + d.getTitle() + "'",
                null, Map.of("documentId", d.getId()));
        try {
            return new Download(d, storage.open(d.getStoredName()));
        } catch (IOException e) {
            throw BusinessException.conflict("FILE_MISSING", "The stored file could not be found. Contact support.");
        }
    }

    private void requirePatient(Long id) {
        if (!patients.existsById(id)) {
            throw BusinessException.notFound("Patient", id);
        }
    }

    private static PatientDocument.Category parseCategory(String raw) {
        try {
            return PatientDocument.Category.valueOf(raw == null ? "" : raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("INVALID_CATEGORY", "Choose a document category.");
        }
    }

    /** Keeps a readable name for display/downloads, stripped of paths and unsafe characters. */
    static String safeName(String original, String contentType) {
        String base = original == null ? "document" : original.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        if (base.isEmpty()) {
            base = "document";
        }
        String ext = switch (contentType) {
            case "application/pdf" -> ".pdf";
            case "image/png" -> ".png";
            default -> ".jpg";
        };
        String lower = base.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(ext) && !(ext.equals(".jpg") && lower.endsWith(".jpeg"))) {
            base = base + ext;
        }
        return base.length() > 200 ? base.substring(base.length() - 200) : base;
    }
}
