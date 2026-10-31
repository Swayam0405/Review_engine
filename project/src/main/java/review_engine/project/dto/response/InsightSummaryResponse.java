package review_engine.project.dto.response;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * Persisted owner summary with its exact period and deterministic sample count.
 */
public record InsightSummaryResponse(UUID id, Instant from, Instant to, String summary, JsonNode positiveHighlights,
                                     JsonNode negativeHighlights, JsonNode recurringIssues,
                                     JsonNode recommendations, int feedbackCount,
                                     Instant latestFeedbackAt, Instant generatedAt) {
}
