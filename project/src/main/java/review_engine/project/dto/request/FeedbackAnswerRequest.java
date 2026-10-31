package review_engine.project.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * One answer whose value field must match the configured question type.
 */
public record FeedbackAnswerRequest(
        @NotNull UUID questionId,
        @Size(max = 5000) String answerText,
        @Min(1) @Max(5) Integer answerRating,
        Boolean answerBoolean) {
}
