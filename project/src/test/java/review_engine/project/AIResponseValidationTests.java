package review_engine.project;

import org.junit.jupiter.api.Test;
import review_engine.project.ai.AIProvider;
import review_engine.project.ai.AIProviderException;
import review_engine.project.ai.AIResponseParser;
import review_engine.project.ai.AIResponseValidator;
import review_engine.project.ai.AIService;
import review_engine.project.ai.dto.AnalysisSentiment;
import review_engine.project.ai.dto.FeedbackAnalysisResult;
import review_engine.project.ai.dto.FollowUpEvaluation;
import review_engine.project.ai.dto.InsightSummaryResult;
import review_engine.project.ai.dto.ReviewReplyResult;
import review_engine.project.config.AIProperties;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AIResponseValidationTests {
    private final AIResponseParser parser = new AIResponseParser(JsonMapper.builder().build());

    @Test
    void parsesValidConversationalEvaluation() {
        FollowUpEvaluation result = parser.parse("""
                {"sufficientFeedback":false,"needsFollowUp":true,"followUpQuestion":"What made the wait difficult?",
                 "reason":"Impact is unclear","identifiedTopics":["Waiting time"],"missingInformation":["Impact"]}
                """, FollowUpEvaluation.class);

        AIResponseValidator.validate(result);
        assertEquals("What made the wait difficult?", result.followUpQuestion());
    }

    @Test
    void rejectsMalformedMissingUnexpectedAndInconsistentOutput() {
        assertThrows(AIProviderException.class,
                () -> parser.parse("{not json}", FollowUpEvaluation.class));
        assertThrows(AIProviderException.class,
                () -> parser.parse("""
                        {"sufficientFeedback":true,"needsFollowUp":false,"followUpQuestion":null,
                         "reason":"","identifiedTopics":[],"missingInformation":[],"extra":"x"}
                        """, FollowUpEvaluation.class));
        assertThrows(AIProviderException.class, () -> AIResponseValidator.validate(
                new FollowUpEvaluation(true, false, "Unexpected question", "", List.of(), List.of())));
    }

    @Test
    void rejectsOutOfRangeAnalysisConfidenceAndInvalidNestedFields() {
        assertThrows(AIProviderException.class, () -> AIResponseValidator.validate(
                new FeedbackAnalysisResult(AnalysisSentiment.POSITIVE, 1.01, "Summary", List.of(),
                        List.of(), List.of(), List.of(), List.of(), List.of())));
        assertThrows(AIProviderException.class, () -> parser.parse("""
                {"sentiment":"POSITIVE","sentimentConfidence":0.8,"sentimentSummary":"Good",
                 "topics":[{"name":"Food","sentiment":"POSITIVE","evidence":"Great food","unknown":true}],
                 "aspects":[],"issues":[],"positiveHighlights":[],"negativeHighlights":[],
                 "improvementOpportunities":[]}
                """, FeedbackAnalysisResult.class));
    }

    @Test
    void handlesEachFeatureSchemaWithoutAcceptingExtraFields() {
        assertEquals("Summary", parser.parse("""
                {"summary":"Summary","positiveHighlights":[],"negativeHighlights":[],
                 "recurringIssues":[],"recommendations":[]}
                """, InsightSummaryResult.class).summary());
        assertEquals("Thank you for sharing.", parser.parse(
                "{\"responseText\":\"Thank you for sharing.\"}", ReviewReplyResult.class).responseText());
        assertThrows(AIProviderException.class, () -> parser.parse(
                "{\"responseText\":\"Thanks\",\"published\":true}", ReviewReplyResult.class));
    }

    @Test
    void aiDisabledReturnsControlledUnavailableErrorWithoutCallingProvider() {
        AIProperties properties = new AIProperties();
        AIProvider provider = mock(AIProvider.class);
        AIService service = new AIService(properties, provider, JsonMapper.builder().build());

        assertThrows(review_engine.project.exception.ApiException.class,
                () -> service.generate("system", "{}", ReviewReplyResult.class));
        org.mockito.Mockito.verifyNoInteractions(provider);
    }
}
