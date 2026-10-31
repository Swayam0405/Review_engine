package review_engine.project.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Request to schedule feedback for an existing completed transaction.
 */
public record CreateFeedbackRequest(
        @NotNull UUID transactionId,
        OffsetDateTime scheduledAt) {
}
