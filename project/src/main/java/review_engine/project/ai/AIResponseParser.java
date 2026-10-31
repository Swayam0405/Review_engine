package review_engine.project.ai;

import review_engine.project.ai.dto.FeedbackAnalysisResult;
import review_engine.project.ai.dto.FollowUpEvaluation;
import review_engine.project.ai.dto.InsightSummaryResult;
import review_engine.project.ai.dto.ReviewReplyResult;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

/**
 * Enforces a per-feature JSON shape before DTO binding and semantic validation.
 */
public class AIResponseParser {
    private final ObjectMapper objectMapper;

    public AIResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public <T> T parse(String json, Class<T> type) {
        try {
            JsonNode root = objectMapper.readTree(json);
            require(root != null && root.isObject(), "AI output must be a JSON object.");
            if (type == FollowUpEvaluation.class) {
                exactFields(root, Set.of("sufficientFeedback", "needsFollowUp", "followUpQuestion",
                        "reason", "identifiedTopics", "missingInformation"));
            } else if (type == FeedbackAnalysisResult.class) {
                exactFields(root, Set.of("sentiment", "sentimentConfidence", "sentimentSummary", "topics",
                        "aspects", "issues", "positiveHighlights", "negativeHighlights", "improvementOpportunities"));
                arrayObjects(root.path("topics"), Set.of("name", "sentiment", "evidence"));
                arrayObjects(root.path("aspects"), Set.of("name", "sentiment", "explanation", "evidence"));
                arrayObjects(root.path("issues"), Set.of("name", "description", "severity", "topic", "evidence"));
            } else if (type == InsightSummaryResult.class) {
                exactFields(root, Set.of("summary", "positiveHighlights", "negativeHighlights",
                        "recurringIssues", "recommendations"));
            } else if (type == ReviewReplyResult.class) {
                exactFields(root, Set.of("responseText"));
            } else {
                throw new AIProviderException("AI response type is not supported.");
            }
            return objectMapper.treeToValue(root, type);
        } catch (AIProviderException exception) {
            throw exception;
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new AIProviderException("AI provider returned malformed structured output.", exception);
        }
    }

    private void arrayObjects(JsonNode array, Set<String> allowedFields) {
        require(array.isArray(), "AI output contains an invalid structured list.");
        for (JsonNode item : array) {
            require(item.isObject(), "AI output contains an invalid structured item.");
            exactFields(item, allowedFields);
        }
    }

    private void exactFields(JsonNode node, Set<String> fields) {
        for (String required : fields) {
            require(node.has(required), "AI output is missing a required field.");
        }
        for (var property : node.properties()) {
            require(fields.contains(property.getKey()), "AI output contains an unexpected field.");
        }
    }

    private void require(boolean valid, String message) {
        if (!valid) {
            throw new AIProviderException(message);
        }
    }
}
