package review_engine.project.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Email address and six-digit code used to verify an email identity.
 */
public record VerifyEmailRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Pattern(regexp = "\\d{6}") String otp) {
}
