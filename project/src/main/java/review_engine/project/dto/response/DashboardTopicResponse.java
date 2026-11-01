package review_engine.project.dto.response;

import java.time.Instant;
import java.util.Map;

/**
 * Distinct-feedback topic counts and sentiment breakdown.
 */
public record DashboardTopicResponse(String topic, long feedbackCount, long positiveMentions,
                                     long neutralMentions, long negativeMentions, long mixedMentions,
                                     double analyzedFeedbackPercentage, Map<String, Long> sentimentDistribution,
                                     Instant from, Instant to) {
}
