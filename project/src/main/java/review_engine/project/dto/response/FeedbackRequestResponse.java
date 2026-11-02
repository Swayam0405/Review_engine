package review_engine.project.dto.response;

import review_engine.project.enums.FeedbackRequestStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Owner-safe feedback request view; token hashes and phone data are never included.
 */
public record FeedbackRequestResponse(
        UUID id,
        UUID transactionId,
        UUID customerId,
        FeedbackRequestStatus status,
        Instant scheduledAt,
        Instant sentAt,
        Instant deliveredAt,
        Instant completedAt,
        Instant expiresAt,
        Instant createdAt,
        String feedbackLink,
        UUID feedbackId) {
}
