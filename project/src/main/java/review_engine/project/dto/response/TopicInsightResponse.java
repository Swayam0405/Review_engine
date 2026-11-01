package review_engine.project.dto.response;

import java.time.Instant;
import java.util.Map;

/**
 * Deterministically aggregated distinct-feedback topic frequency.
 */
public record TopicInsightResponse(String topic, long mentionCount, Map<String, Long> sentimentDistribution,
                                   Instant from, Instant to) {
}
