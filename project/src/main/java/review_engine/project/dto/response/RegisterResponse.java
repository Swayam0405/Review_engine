package review_engine.project.dto.response;

/**
 * Safe indication that registration requires email verification.
 *
 * @param emailVerificationRequired whether verification is still pending
 * @param developmentOtp a code returned only when explicitly enabled in the dev profile
 */
public record RegisterResponse(boolean emailVerificationRequired, String developmentOtp) {
}
