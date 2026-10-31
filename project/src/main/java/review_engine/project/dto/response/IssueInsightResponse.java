package review_engine.project.dto.response;

import java.time.Instant;
import java.util.Map;

/**
 * Distinct-feedback issue recurrence, evidence description, severity counts, and related topics.
 */
public record IssueInsightResponse(String name, String description, long feedbackFrequency,
                                   Map<String, Long> severityDistribution, String relatedTopics,
                                   Instant from, Instant to) {
}
