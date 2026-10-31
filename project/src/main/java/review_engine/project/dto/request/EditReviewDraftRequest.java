package review_engine.project.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Owner-edited public response text, validated before the draft can be approved.
 */
public record EditReviewDraftRequest(
        @NotBlank @Size(max = 2000) @Pattern(regexp = "^[^\\p{Cc}]*$") String responseText) {
}
