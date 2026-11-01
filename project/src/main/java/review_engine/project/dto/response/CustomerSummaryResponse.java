package review_engine.project.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Customer collection view intentionally omits phone data to avoid unnecessary decryption and exposure.
 */
public record CustomerSummaryResponse(UUID id, String name, Instant createdAt, Instant updatedAt) {
}
