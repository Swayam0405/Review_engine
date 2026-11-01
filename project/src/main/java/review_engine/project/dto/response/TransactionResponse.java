package review_engine.project.dto.response;

import review_engine.project.enums.TransactionStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Safe transaction view containing customer identity but no cryptographic customer fields.
 */
public record TransactionResponse(
        UUID id,
        UUID customerId,
        String customerName,
        String referenceCode,
        TransactionStatus status,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt) {
}
