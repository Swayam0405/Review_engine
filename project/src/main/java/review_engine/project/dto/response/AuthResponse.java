package review_engine.project.dto.response;

/**
 * Application access-token response; refresh tokens are sent only as cookies.
 *
 * @param accessToken short-lived signed application access token
 * @param tokenType authorization scheme for the access token
 * @param user authenticated user and server-derived onboarding status
 */
public record AuthResponse(String accessToken, String tokenType, CurrentUserResponse user) {
}
