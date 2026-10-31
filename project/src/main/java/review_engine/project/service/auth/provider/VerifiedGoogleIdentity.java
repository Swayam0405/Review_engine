package review_engine.project.service.auth.provider;

/**
 * Claims returned only after successful Google identity-token verification.
 *
 * @param subject Google's stable identity subject
 * @param email verified Google email address
 * @param name display name from the verified token, when provided
 */
public record VerifiedGoogleIdentity(String subject, String email, String name) {
}
