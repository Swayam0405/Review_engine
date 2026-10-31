package review_engine.project.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Replacement Google review URL for an existing business profile.
 */
public record GoogleReviewUrlRequest(@NotBlank @Size(max = 2048) String googleReviewUrl) {
}
