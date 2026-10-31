package review_engine.project.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Email address for a safe verification-code resend request.
 */
public record ResendOtpRequest(@NotBlank @Email @Size(max = 320) String email) {
}
