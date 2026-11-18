package lk.hms.modules.patients.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Metadata only; file bytes live on disk under HMS_UPLOAD_DIR with a random name. */
@Entity
@Table(name = "patient_documents")
@Getter
@Setter
@NoArgsConstructor
public class PatientDocument {

    public enum Category { ID_PROOF, REFERRAL, LAB_EXTERNAL, IMAGING, DISCHARGE, CONSENT, OTHER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Category category;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "stored_name", nullable = false, columnDefinition = "char(36)")
    private String storedName;

    @Column(name = "content_type", nullable = false, length = 60)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false, columnDefinition = "char(64)")
    private String sha256;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
