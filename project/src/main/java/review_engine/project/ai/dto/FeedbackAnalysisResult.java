package review_engine.project.ai.dto;

import java.util.List;

/**
 * Structured analysis output validated before persistence; confidence is a model self-score from 0 to 1.
 */
public record FeedbackAnalysisResult(AnalysisSentiment sentiment, Double sentimentConfidence, String sentimentSummary,
                                    List<TopicAnalysis> topics, List<AspectAnalysis> aspects,
                                    List<IssueAnalysis> issues, List<String> positiveHighlights,
                                    List<String> negativeHighlights, List<String> improvementOpportunities) {
}
