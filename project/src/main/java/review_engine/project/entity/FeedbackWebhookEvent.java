package review_engine.project.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Persisted callback identity used to make provider delivery notifications idempotent.
 */
@Entity
@Table(name = "feedback_webhook_events")
@Getter
@Setter
@NoArgsConstructor
public class FeedbackWebhookEvent {
    @Id
    @Column(name = "event_id", length = 128)
    private String eventId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "feedback_request_id", nullable = false)
    private FeedbackRequest feedbackRequest;

    @Column(name = "provider_message_id", length = 255)
    private String providerMessageId;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @PrePersist
    void onCreate() {
        if (receivedAt == null) receivedAt = Instant.now();
    }
}
