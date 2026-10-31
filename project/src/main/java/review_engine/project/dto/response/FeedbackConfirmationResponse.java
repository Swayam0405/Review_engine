package review_engine.project.dto.response;

/**
 * Minimal confirmation returned after a successful public feedback submission.
 */
public record FeedbackConfirmationResponse(boolean received, String message) {
}
