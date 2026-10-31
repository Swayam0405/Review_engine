package review_engine.project.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Business profile fields supplied during onboarding or profile updates.
 */
public record BusinessRequest(
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 100) String category,
        @NotBlank @Size(max = 500) String address,
        @NotBlank @Size(max = 64) String timezone,
        @Size(max = 2048) String googleReviewUrl) {
}
