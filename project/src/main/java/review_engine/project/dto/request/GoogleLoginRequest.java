package review_engine.project.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Google identity token returned by the official client sign-in flow.
 */
public record GoogleLoginRequest(@NotBlank @Size(max = 8192) String idToken) {
}
