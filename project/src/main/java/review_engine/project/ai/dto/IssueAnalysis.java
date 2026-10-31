package review_engine.project.ai.dto;

/**
 * Actionable issue extracted from actual customer feedback.
 */
public record IssueAnalysis(String name, String description, IssueSeverity severity, String topic, String evidence) {
}
