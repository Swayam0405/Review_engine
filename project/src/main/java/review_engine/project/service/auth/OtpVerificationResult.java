package review_engine.project.service.auth;

/**
 * Outcomes persisted by OTP verification before the controller maps failures to HTTP.
 */
public enum OtpVerificationResult {
    VERIFIED,
    INVALID,
    EXPIRED,
    ATTEMPTS_EXCEEDED
}
