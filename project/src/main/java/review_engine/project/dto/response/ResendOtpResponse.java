package review_engine.project.dto.response;

/**
 * Generic resend result; the optional code is populated only in explicit local dev mode.
 *
 * @param developmentOtp verification code for local development, otherwise null
 */
public record ResendOtpResponse(String developmentOtp) {
}
