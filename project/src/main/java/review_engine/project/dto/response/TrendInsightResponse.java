package review_engine.project.dto.response;

import java.time.Instant;
import java.util.Map;

/**
 * UTC daily feedback volume, average rating, and available analysis counts.
 */
public record TrendInsightResponse(Instant from, Instant to, java.util.List<TrendPoint> points) {
    public record TrendPoint(Instant day, long feedbackCount, double averageRating,
                             Map<String, Long> sentimentDistribution) {
    }
}
