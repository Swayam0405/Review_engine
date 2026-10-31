package review_engine.project.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Imported Google review record; this entity does not itself synchronize with Google.
 */
@Entity
@Table(name = "google_reviews", uniqueConstraints = {
        @UniqueConstraint(name = "uk_google_reviews_business_source",
                columnNames = {"business_id", "source_review_id"}),
        @UniqueConstraint(name = "uk_google_reviews_id_business", columnNames = {"id", "business_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class GoogleReview {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @Column(name = "source_review_id", nullable = false, length = 255)
    private String sourceReviewId;

    @Column(name = "reviewer_display_name", length = 255)
    private String reviewerDisplayName;

    @Column(nullable = false)
    private int rating;

    @Column(name = "review_text", columnDefinition = "text")
    private String reviewText;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
