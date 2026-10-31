package review_engine.project.ai.dto;

/**
 * AI-extracted business aspect with sentiment and a short grounded evidence span.
 */
public record AspectAnalysis(String name, AnalysisSentiment sentiment, String explanation, String evidence) {
}
