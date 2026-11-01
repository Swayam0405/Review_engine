package review_engine.project.dto.response;

import review_engine.project.enums.FeedbackQuestionType;

import java.time.Instant;
import java.util.UUID;

/**
 * Safe representation of a business-owned feedback question.
 */
public record FeedbackQuestionResponse(
        UUID id,
        String questionText,
        FeedbackQuestionType questionType,
        boolean required,
        boolean active,
        int displayOrder,
        Instant createdAt,
        Instant updatedAt) {
}
