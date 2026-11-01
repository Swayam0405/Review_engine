package review_engine.project.dto.response;

import java.time.Instant;
import java.util.Map;

/**
 * A recurring issue supported by distinct analyzed feedback submissions.
 */
public record DashboardIssueResponse(String issue, String description, long distinctFeedbackReports,
                                     Map<String, Long> severityDistribution, String relatedTopics,
                                     Instant firstReportedAt, Instant mostRecentlyReportedAt,
                                     Map<String, Long> sentimentDistribution) {
}
