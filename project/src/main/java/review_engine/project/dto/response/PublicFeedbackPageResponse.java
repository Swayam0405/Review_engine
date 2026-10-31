package review_engine.project.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * Minimal information required to render the public feedback experience.
 */
public record PublicFeedbackPageResponse(
        String businessName,
        Instant expiresAt,
        List<FeedbackQuestionResponse> questions) {
}
