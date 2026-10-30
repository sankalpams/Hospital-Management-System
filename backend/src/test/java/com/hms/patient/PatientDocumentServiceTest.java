package com.hms.patient;

import com.hms.audit.AuditService;
import com.hms.common.AppException;
import com.hms.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PatientDocumentServiceTest {

    @Mock private PatientDocumentRepository documentRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private VirusScanner virusScanner;
    @Mock private AuditService auditService;

    @TempDir
    Path tempDir;

    private PatientDocumentService documentService;
    private Patient samplePatient;
    private UUID patientId;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
                new AppProperties.Cors("http://localhost:8080"),
                new AppProperties.Security("ZGV2LXNlY3JldC1tdXN0LWJlLWF0LWxlYXN0LTMyLWJ5dGVzLWxvbmc=", 15, 7, 15, 5),
                new AppProperties.Mail("no-reply@hms.local", "http://localhost:8080/reset-password"),
                new AppProperties.Storage(tempDir.toString())
        );

        documentService = new PatientDocumentService(
                documentRepository, patientRepository, virusScanner, auditService, properties
        );

        patientId = UUID.randomUUID();
        samplePatient = new Patient();
        samplePatient.setId(patientId);
        samplePatient.setMrn("MRN-2026-000001");
    }

    @Test
    @DisplayName("Upload valid PDF saves document file to configured storage directory outside web root")
    void upload_ValidPdf_Success() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "lab-results.pdf", "application/pdf", "%PDF-1.4 sample content".getBytes()
        );

        when(virusScanner.scan(any(InputStream.class), eq("lab-results.pdf")))
                .thenReturn(VirusScanner.ScanResult.ok());
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(samplePatient));
        when(documentRepository.save(any(PatientDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        PatientDocument saved = documentService.upload(patientId, file, DocumentCategory.LAB_REPORT, "Hematology report");

        assertThat(saved).isNotNull();
        assertThat(saved.getFileName()).isEqualTo("lab-results.pdf");
        assertThat(saved.getContentType()).isEqualTo("application/pdf");
        assertThat(saved.getCategory()).isEqualTo(DocumentCategory.LAB_REPORT);
        verify(documentRepository).save(any(PatientDocument.class));
    }

    @Test
    @DisplayName("Upload with unauthorized file type (e.g. .exe) is rejected with UNSUPPORTED_MEDIA_TYPE")
    void upload_UnauthorizedExtension_Rejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "malicious.exe", "application/octet-stream", "MZ...".getBytes()
        );

        assertThatThrownBy(() -> documentService.upload(patientId, file, DocumentCategory.OTHER, null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
                    assertThat(appEx.getCode()).isEqualTo("INVALID_FILE_TYPE");
                });
    }

    @Test
    @DisplayName("Upload exceeding 10 MB limit is rejected with PAYLOAD_TOO_LARGE")
    void upload_Exceeds10MB_Rejected() {
        byte[] oversized = new byte[11 * 1024 * 1024]; // 11MB
        MockMultipartFile file = new MockMultipartFile(
                "file", "huge-scan.png", "image/png", oversized
        );

        assertThatThrownBy(() -> documentService.upload(patientId, file, DocumentCategory.SCAN, null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
                    assertThat(appEx.getCode()).isEqualTo("FILE_TOO_LARGE");
                });
    }

    @Test
    @DisplayName("Virus scan detection aborts file upload")
    void upload_InfectedFile_Aborted() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "scan.pdf", "application/pdf", "%PDF-infected".getBytes()
        );

        when(virusScanner.scan(any(InputStream.class), eq("scan.pdf")))
                .thenReturn(VirusScanner.ScanResult.infected("Signature match: Trojan.PDF.Gen"));

        assertThatThrownBy(() -> documentService.upload(patientId, file, DocumentCategory.SCAN, null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("File security verification failed");

        verify(documentRepository, never()).save(any());
    }
}

