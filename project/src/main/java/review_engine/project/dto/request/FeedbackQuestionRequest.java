package review_engine.project.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import review_engine.project.enums.FeedbackQuestionType;

/**
 * Editable business-owned feedback question fields.
 */
public record FeedbackQuestionRequest(
        @NotBlank @Size(max = 1000) @Pattern(regexp = "^[^\\p{Cc}]*$") String questionText,
        @NotNull FeedbackQuestionType questionType,
        boolean required,
        @Min(0) int displayOrder,
        boolean active) {
}
