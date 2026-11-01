package review_engine.project.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Public business profile representation without ownership mutation fields.
 */
public record BusinessResponse(
        UUID id,
        String name,
        String category,
        String address,
        String timezone,
        String googleReviewUrl,
        Instant createdAt,
        Instant updatedAt) {
}
