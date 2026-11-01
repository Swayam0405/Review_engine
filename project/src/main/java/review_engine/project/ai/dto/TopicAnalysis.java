package review_engine.project.ai.dto;

/**
 * Normalized feedback topic and evidence-backed sentiment.
 */
public record TopicAnalysis(String name, AnalysisSentiment sentiment, String evidence) {
}
