package review_engine.project.dto.response;

import java.time.Instant;
import java.util.Map;

/**
 * Deterministic rating and analyzed-sentiment metrics for one UTC period.
 */
public record InsightsOverviewResponse(Instant from, Instant to, long totalFeedback, double averageRating,
                                       Map<Integer, Long> ratingDistribution,
                                       Map<String, Long> sentimentDistribution) {
}
