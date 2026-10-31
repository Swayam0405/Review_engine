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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import review_engine.project.enums.FeedbackConversationStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * A single customer-facing feedback conversation attached to one request.
 */
@Entity
@Table(name = "feedback_conversations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_feedback_conversations_request", columnNames = "feedback_request_id"),
        @UniqueConstraint(name = "uk_feedback_conversations_id_business", columnNames = {"id", "business_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class FeedbackConversation {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feedback_request_id", nullable = false, unique = true)
    private FeedbackRequest feedbackRequest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FeedbackConversationStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

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
