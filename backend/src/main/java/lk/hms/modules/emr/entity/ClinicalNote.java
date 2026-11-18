package lk.hms.modules.emr.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;


/** Editable by its author until signed; afterwards immutable and corrected only by addenda. */
@Entity
@Table(name = "clinical_notes")
@Getter
@Setter
@NoArgsConstructor
public class ClinicalNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "note_type", nullable = false, length = 20)
    private String noteType;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "author_user_id", nullable = false)
    private Long authorUserId;

    @Column(name = "parent_note_id")
    private Long parentNoteId;

    @Column(name = "signed_at")
    private Instant signedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
