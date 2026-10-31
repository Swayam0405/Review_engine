package review_engine.project.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import review_engine.project.enums.ReviewResponseDraftStatus;
import review_engine.project.enums.ResponseTone;

import java.time.Instant;
import java.util.UUID;

/**
 * Owner-reviewed AI reply draft; approval is distinct from Google publication.
 */
@Entity
@Table(name = "review_response_drafts")
@Getter
@Setter
@NoArgsConstructor
public class ReviewResponseDraft {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "google_review_id", nullable = false)
    private GoogleReview googleReview;

    @Column(name = "generated_text", nullable = false, columnDefinition = "text")
    private String generatedText;

    @Column(name = "response_text", nullable = false, columnDefinition = "text")
    private String responseText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReviewResponseDraftStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ResponseTone tone;

    @Column(name = "ai_provider", nullable = false, length = 64)
    private String aiProvider;

    @Column(name = "ai_model", nullable = false, length = 128)
    private String aiModel;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        generatedAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
