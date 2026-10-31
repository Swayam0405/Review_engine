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
import review_engine.project.enums.FeedbackRequestStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * One business invitation and its delivery lifecycle for a completed transaction.
 */
@Entity
@Table(name = "feedback_requests", uniqueConstraints = {
        @UniqueConstraint(name = "uk_feedback_requests_transaction", columnNames = "transaction_id"),
        @UniqueConstraint(name = "uk_feedback_requests_id_business", columnNames = {"id", "business_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class FeedbackRequest {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private TransactionEntity transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "request_token_hash", nullable = false, length = 64, unique = true)
    private String requestTokenHash;

    @Column(name = "delivery_event_id", nullable = false)
    private UUID deliveryEventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FeedbackRequestStatus status;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @Column(name = "provider_message_id", length = 255)
    private String providerMessageId;

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
