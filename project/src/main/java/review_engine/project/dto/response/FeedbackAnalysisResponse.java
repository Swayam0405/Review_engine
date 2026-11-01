package review_engine.project.dto.response;

import review_engine.project.ai.dto.AspectAnalysis;
import review_engine.project.ai.dto.AnalysisSentiment;
import review_engine.project.ai.dto.IssueAnalysis;
import review_engine.project.ai.dto.TopicAnalysis;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Validated analysis data and processing state for one owner-visible feedback record.
 */
public record FeedbackAnalysisResponse(UUID feedbackId, String status, AnalysisSentiment sentiment,
                                       Double sentimentConfidence, String sentimentSummary,
                                       List<TopicAnalysis> topics, List<AspectAnalysis> aspects,
                                       List<IssueAnalysis> issues, List<String> positiveHighlights,
                                       List<String> negativeHighlights, List<String> improvementOpportunities,
                                       Instant analyzedAt) {
}
