package review_engine.project.dto.response;

import java.time.Instant;
import java.util.Map;

/**
 * Aspect-level sentiment counts from successfully analyzed feedback.
 */
public record DashboardAspectResponse(String aspect, long mentions, Map<String, Long> sentimentDistribution,
                                      double averageSentimentConfidence, Instant from, Instant to) {
}
