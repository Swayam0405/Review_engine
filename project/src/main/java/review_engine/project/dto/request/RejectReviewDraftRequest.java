package review_engine.project.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Optional owner-provided reason when rejecting a generated draft.
 */
public record RejectReviewDraftRequest(
        @Size(max = 1000) @Pattern(regexp = "^[^\\p{Cc}]*$") String reason) {
}
