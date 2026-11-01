package review_engine.project.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Final customer rating and optional structured answers.
 */
public record FeedbackSubmissionRequest(
        @NotNull @Min(1) @Max(5) Integer rating,
        @Size(max = 5000) String comment,
        @Size(max = 100) List<@Valid FeedbackAnswerRequest> answers) {
}
