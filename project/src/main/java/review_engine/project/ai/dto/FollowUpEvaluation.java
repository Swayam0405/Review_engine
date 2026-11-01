package review_engine.project.ai.dto;

import java.util.List;

/**
 * Validated conversational evaluation; internal reason and missing fields are not sent to customers.
 */
public record FollowUpEvaluation(Boolean sufficientFeedback, Boolean needsFollowUp, String followUpQuestion,
                                 String reason, List<String> identifiedTopics, List<String> missingInformation) {
}
