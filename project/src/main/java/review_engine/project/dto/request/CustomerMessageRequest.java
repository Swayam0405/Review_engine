package review_engine.project.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Customer-authored conversation text; sender identity is assigned by the server.
 */
public record CustomerMessageRequest(
        @NotBlank @Size(max = 10000) @Pattern(regexp = "^[^\\p{Cc}]*$") String content,
        @Min(1) @Max(5) Integer rating) {
}
