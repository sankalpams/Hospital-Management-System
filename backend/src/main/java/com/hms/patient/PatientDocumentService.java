package com.hms.patient;

import com.hms.audit.AuditAction;
import com.hms.audit.AuditService;
import com.hms.common.AppException;
import com.hms.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PatientDocumentService {
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "image/webp"
    );
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "png", "jpg", "jpeg", "webp"
    );

    private final PatientDocumentRepository documentRepository;
    private final PatientRepository patientRepository;
    private final VirusScanner virusScanner;
    private final AuditService auditService;
    private final AppProperties properties;

    public record DocumentFileDownload(Resource resource, String contentType, String fileName) {}

    @Transactional
    public PatientDocument upload(UUID patientId, MultipartFile file, DocumentCategory category, String notes) {
        if (file.isEmpty()) {
            throw AppException.business("The uploaded file is empty.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new AppException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "File size exceeds the 10 MB limit.");
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "document");
        String extension = getExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new AppException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "INVALID_FILE_TYPE",
                    "Unsupported file type. Only PDF and images (PNG, JPEG, WEBP) are accepted.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new AppException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "INVALID_FILE_TYPE",
                    "Unsupported content type. Only PDF and images (PNG, JPEG, WEBP) are accepted.");
        }

        // Virus scan hook
        try (InputStream is = file.getInputStream()) {
            VirusScanner.ScanResult scan = virusScanner.scan(is, originalFilename);
            if (!scan.clean()) {
                throw AppException.business("File security verification failed: " + scan.details());
            }
        } catch (IOException e) {
            throw AppException.business("Could not inspect uploaded file.");
        }

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> AppException.notFound("Patient not found."));

        // Save file outside web root
        String storageFilename = UUID.randomUUID() + "." + extension;
        Path targetDir = getUploadPath();
        try {
            Files.createDirectories(targetDir);
            Path targetPath = targetDir.resolve(storageFilename).normalize();
            if (!targetPath.startsWith(targetDir)) {
                throw AppException.business("Invalid file destination path.");
            }
            try (InputStream is = file.getInputStream()) {
                Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not store file on disk: " + e.getMessage(), e);
        }

        PatientDocument doc = new PatientDocument(
                patient,
                originalFilename,
                storageFilename,
                contentType,
                file.getSize(),
                category != null ? category : DocumentCategory.OTHER,
                notes != null ? notes.trim() : null
        );

        PatientDocument saved = documentRepository.save(doc);
        auditService.record(AuditAction.CREATE, "PatientDocument", saved.getId().toString(), null, null);
        return saved;
    }

    @Transactional(readOnly = true)
    public DocumentFileDownload download(UUID patientId, UUID documentId) {
        PatientDocument doc = documentRepository.findByIdAndPatientId(documentId, patientId)
                .orElseThrow(() -> AppException.notFound("Document not found."));

        Path filePath = getUploadPath().resolve(doc.getStoredPath()).normalize();
        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw AppException.notFound("File is no longer available on disk.");
            }
            auditService.record(AuditAction.VIEW, "PatientDocument", doc.getId().toString(), null, null);
            return new DocumentFileDownload(resource, doc.getContentType(), doc.getFileName());
        } catch (MalformedURLException e) {
            throw AppException.notFound("File could not be accessed.");
        }
    }

    @Transactional
    public void delete(UUID patientId, UUID documentId) {
        PatientDocument doc = documentRepository.findByIdAndPatientId(documentId, patientId)
                .orElseThrow(() -> AppException.notFound("Document not found."));

        Path filePath = getUploadPath().resolve(doc.getStoredPath()).normalize();
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException ignored) {}

        documentRepository.delete(doc);
        auditService.record(AuditAction.DELETE, "PatientDocument", doc.getId().toString(), null, null);
    }

    public List<PatientDocument> listForPatient(UUID patientId) {
        return documentRepository.findByPatientIdOrderByCreatedAtDesc(patientId);
    }

    private Path getUploadPath() {
        String dir = properties.storage() != null && properties.storage().uploadDir() != null
                ? properties.storage().uploadDir()
                : "./uploads/documents";
        return Paths.get(dir).toAbsolutePath().normalize();
    }

    private String getExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        return idx > 0 ? filename.substring(idx + 1) : "";
    }
}

