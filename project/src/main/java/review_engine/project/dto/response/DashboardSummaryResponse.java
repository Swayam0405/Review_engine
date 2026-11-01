package review_engine.project.dto.response;

import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * Persisted Phase 5 AI summary augmented with the dashboard's measured coverage.
 */
public record DashboardSummaryResponse(UUID id, Instant from, Instant to, String summary,
                                       JsonNode positiveObservations, JsonNode mainConcerns,
                                       JsonNode recurringIssues, JsonNode improvementRecommendations,
                                       int feedbackCountUsed, Instant latestFeedbackAt, Instant generatedAt,
                                       DashboardOverviewResponse.AnalysisCoverage analysisCoverage) {
}
