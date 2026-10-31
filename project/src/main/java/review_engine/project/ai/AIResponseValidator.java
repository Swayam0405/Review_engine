package review_engine.project.ai;

import review_engine.project.ai.dto.AspectAnalysis;
import review_engine.project.ai.dto.FeedbackAnalysisResult;
import review_engine.project.ai.dto.FollowUpEvaluation;
import review_engine.project.ai.dto.InsightSummaryResult;
import review_engine.project.ai.dto.IssueAnalysis;
import review_engine.project.ai.dto.ReviewReplyResult;

/**
 * Applies strict field, size, and cross-field validation to untrusted model output.
 */
public final class AIResponseValidator {
    private AIResponseValidator() {
    }

    public static void validate(Object response) {
        if (response instanceof FollowUpEvaluation result) {
            require(result.sufficientFeedback() != null && result.needsFollowUp() != null,
                    "AI conversation result is incomplete.");
            require(result.needsFollowUp() == (result.followUpQuestion() != null),
                    "AI follow-up fields are inconsistent.");
            if (result.followUpQuestion() != null) {
                text(result.followUpQuestion(), 1, 500, "AI follow-up question is invalid.");
            }
            texts(result.identifiedTopics(), 20, 100, "AI topics are invalid.");
            texts(result.missingInformation(), 10, 300, "AI missing-information fields are invalid.");
            text(result.reason(), 0, 500, "AI reasoning field is invalid.");
        } else if (response instanceof FeedbackAnalysisResult result) {
            require(result.sentiment() != null, "AI sentiment is required.");
            require(result.sentimentConfidence() != null && result.sentimentConfidence() >= 0
                    && result.sentimentConfidence() <= 1, "AI confidence must be from 0 to 1.");
            text(result.sentimentSummary(), 1, 1000, "AI sentiment summary is invalid.");
            require(result.topics() != null && result.topics().size() <= 20, "AI topics are invalid.");
            for (var topic : result.topics()) {
                require(topic != null && topic.sentiment() != null, "AI topic sentiment is invalid.");
                text(topic.name(), 1, 100, "AI topic name is invalid.");
                text(topic.evidence(), 1, 500, "AI topic evidence is invalid.");
            }
            require(result.aspects() != null && result.aspects().size() <= 30, "AI aspects are invalid.");
            require(result.issues() != null && result.issues().size() <= 30, "AI issues are invalid.");
            for (AspectAnalysis aspect : result.aspects()) {
                require(aspect != null && aspect.sentiment() != null, "AI aspect sentiment is invalid.");
                text(aspect.name(), 1, 100, "AI aspect name is invalid.");
                text(aspect.explanation(), 1, 500, "AI aspect explanation is invalid.");
                text(aspect.evidence(), 0, 500, "AI aspect evidence is invalid.");
            }
            for (IssueAnalysis issue : result.issues()) {
                require(issue != null && issue.severity() != null, "AI issue severity is invalid.");
                text(issue.name(), 1, 100, "AI issue name is invalid.");
                text(issue.description(), 1, 500, "AI issue description is invalid.");
                text(issue.topic(), 0, 100, "AI issue topic is invalid.");
                text(issue.evidence(), 1, 500, "AI issue evidence is invalid.");
            }
            texts(result.positiveHighlights(), 20, 500, "AI positive highlights are invalid.");
            texts(result.negativeHighlights(), 20, 500, "AI negative highlights are invalid.");
            texts(result.improvementOpportunities(), 20, 500, "AI improvement opportunities are invalid.");
        } else if (response instanceof InsightSummaryResult result) {
            text(result.summary(), 1, 3000, "AI insight summary is invalid.");
            texts(result.positiveHighlights(), 20, 500, "AI positive highlights are invalid.");
            texts(result.negativeHighlights(), 20, 500, "AI negative highlights are invalid.");
            texts(result.recurringIssues(), 20, 500, "AI recurring issues are invalid.");
            texts(result.recommendations(), 20, 500, "AI recommendations are invalid.");
        } else if (response instanceof ReviewReplyResult result) {
            text(result.responseText(), 1, 2000, "AI review reply is invalid.");
        } else {
            throw new AIProviderException("AI response type is not supported.");
        }
    }

    private static void texts(java.util.List<String> values, int maxItems, int maxLength, String error) {
        require(values != null && values.size() <= maxItems, error);
        values.forEach(value -> text(value, 1, maxLength, error));
    }

    private static void text(String value, int min, int max, String error) {
        require(value != null && value.strip().length() >= min && value.length() <= max
                && value.codePoints().noneMatch(Character::isISOControl), error);
    }

    private static void require(boolean valid, String error) {
        if (!valid) {
            throw new AIProviderException(error);
        }
    }
}
