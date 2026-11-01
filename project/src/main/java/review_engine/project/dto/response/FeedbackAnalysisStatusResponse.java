package review_engine.project.dto.response;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * Owner-scoped job status and optional completed analysis payload.
 */
public record FeedbackAnalysisStatusResponse(UUID feedbackId, String status, JsonNode analysis,
                                            String safeError, Instant analyzedAt) {
}
