package review_engine.project.dto.response;

import java.time.Instant;
import java.util.Map;

/**
 * Deterministic rating, analysis coverage, and sentiment metrics for one business period.
 */
public record DashboardOverviewResponse(Period period, FeedbackMetrics feedbackMetrics,
                                       Map<Integer, RatingMetric> ratingDistribution,
                                       Map<String, SentimentMetric> sentimentDistribution,
                                       AnalysisCoverage analysisCoverage,
                                       PeriodComparison comparison) {
    public record Period(Instant from, Instant to) {
    }

    public record FeedbackMetrics(long totalCompletedFeedback, double averageRating,
                                  long positiveFeedback, long neutralFeedback, long negativeFeedback,
                                  long mixedFeedback) {
    }

    public record RatingMetric(long count, double percentage) {
    }

    public record SentimentMetric(long count, double percentage) {
    }

    public record AnalysisCoverage(long totalFeedback, long analyzed, long pending, long failed,
                                   double analyzedPercentage) {
    }

    public record PeriodComparison(Instant previousFrom, Instant previousTo, long previousFeedbackCount,
                                   long currentFeedbackCount, Double percentageChange) {
    }
}
