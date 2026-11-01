package review_engine.project.dto.response;

/**
 * Customer-safe confirmation; Google review facilitation is optional and unconditional on rating.
 */
public record FeedbackConfirmationResponse(boolean received, String message, boolean feedbackCompleted,
                                           boolean conversationCompleted, String completionMessage,
                                           boolean googleReviewAvailable, String googleReviewUrl,
                                           String reviewInvitationMessage) {
}
