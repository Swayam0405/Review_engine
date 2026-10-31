package review_engine.project.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import review_engine.project.dto.response.FeedbackAnalysisStatusResponse;
import review_engine.project.entity.FeedbackAnalysis;
import review_engine.project.enums.FeedbackOutboxStatus;
import review_engine.project.exception.ApiException;
import review_engine.project.repository.FeedbackAnalysisRepository;
import review_engine.project.repository.FeedbackOutboxRepository;
import review_engine.project.repository.FeedbackRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves analysis state only after verifying that the feedback belongs to the caller's business.
 */
@Service
public class FeedbackAnalysisQueryService {
    private final BusinessScopeService businessScope;
    private final FeedbackRepository feedbackRepository;
    private final FeedbackAnalysisRepository analysisRepository;
    private final FeedbackOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public FeedbackAnalysisQueryService(BusinessScopeService businessScope, FeedbackRepository feedbackRepository,
                                        FeedbackAnalysisRepository analysisRepository,
                                        FeedbackOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.businessScope = businessScope;
        this.feedbackRepository = feedbackRepository;
        this.analysisRepository = analysisRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public FeedbackAnalysisStatusResponse get(UUID ownerId, UUID feedbackId) {
        UUID businessId = businessScope.requireBusiness(ownerId).getId();
        if (!feedbackRepository.existsByIdAndBusinessId(feedbackId, businessId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Feedback was not found.");
        }
        FeedbackAnalysis analysis = analysisRepository.findByFeedbackIdAndBusinessId(feedbackId, businessId)
                .orElse(null);
        if (analysis != null) {
            return new FeedbackAnalysisStatusResponse(feedbackId, "COMPLETED", toJson(analysis), null,
                    analysis.getAnalyzedAt());
        }
        var job = outboxRepository.findByEventTypeAndAggregateId(
                FeedbackAnalysisJobService.EVENT_TYPE, feedbackId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Analysis job was not found."));
        String safeError = job.getStatus() == FeedbackOutboxStatus.FAILED
                ? "Analysis failed after the configured retry limit." : null;
        return new FeedbackAnalysisStatusResponse(feedbackId, job.getStatus().name(), null,
                safeError, null);
    }

    private JsonNode toJson(FeedbackAnalysis analysis) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sentiment", analysis.getSentiment());
        result.put("sentimentConfidence", analysis.getSentimentConfidence());
        result.put("sentimentSummary", analysis.getSentimentSummary());
        result.put("topics", parse(analysis.getTopics()));
        result.put("aspects", parse(analysis.getAspects()));
        result.put("issues", parse(analysis.getIssues()));
        result.put("positiveHighlights", parse(analysis.getPositiveHighlights()));
        result.put("negativeHighlights", parse(analysis.getNegativeHighlights()));
        result.put("improvementOpportunities", parse(analysis.getImprovementOpportunities()));
        return objectMapper.valueToTree(result);
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Persisted analysis JSON is invalid.", exception);
        }
    }
}
