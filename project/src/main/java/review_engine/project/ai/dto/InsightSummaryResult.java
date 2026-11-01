package review_engine.project.ai.dto;

import java.util.List;

/**
 * Grounded AI interpretation of deterministic business metrics and analyzed feedback.
 */
public record InsightSummaryResult(String summary, List<String> positiveHighlights,
                                   List<String> negativeHighlights, List<String> recurringIssues,
                                   List<String> recommendations) {
}
