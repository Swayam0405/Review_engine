package review_engine.project.dto.response;

import review_engine.project.enums.FeedbackConversationStatus;

import java.time.Instant;
import java.util.List;

/**
 * Public-safe feedback conversation projection.
 */
public record FeedbackConversationResponse(
        FeedbackConversationStatus status,
        Instant startedAt,
        Instant completedAt,
        List<ConversationMessageResponse> messages,
        List<FeedbackQuestionResponse> questions) {
}
