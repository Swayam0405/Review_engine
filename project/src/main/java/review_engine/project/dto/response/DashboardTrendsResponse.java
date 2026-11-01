package review_engine.project.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * UTC time buckets for deterministic ratings and successfully analyzed sentiment.
 */
public record DashboardTrendsResponse(Instant from, Instant to, Interval interval, List<Point> points) {
    public enum Interval {
        DAILY,
        WEEKLY,
        MONTHLY
    }

    public record Point(Instant periodStart, Instant periodEnd, long feedbackCount, double averageRating,
                        Map<String, Long> sentimentDistribution,
                        DashboardOverviewResponse.AnalysisCoverage analysisCoverage) {
    }
}
