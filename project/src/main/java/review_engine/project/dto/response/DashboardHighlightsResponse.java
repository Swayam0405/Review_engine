package review_engine.project.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * Grounded positive and negative summaries extracted from persisted analysis.
 */
public record DashboardHighlightsResponse(Instant from, Instant to, List<Highlight> positive,
                                          List<Highlight> negative) {
    public record Highlight(String topicOrAspect, String explanation, long supportingFeedbackRecords,
                            String sentiment) {
    }
}
