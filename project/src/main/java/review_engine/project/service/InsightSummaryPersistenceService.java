package review_engine.project.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import review_engine.project.ai.dto.InsightSummaryResult;
import review_engine.project.entity.InsightSummary;
import review_engine.project.repository.BusinessRepository;
import review_engine.project.repository.InsightSummaryRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Persists validated AI summaries independently from external provider calls.
 */
@Service
public class InsightSummaryPersistenceService {
    private final InsightSummaryRepository repository;
    private final BusinessRepository businessRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public InsightSummaryPersistenceService(InsightSummaryRepository repository,
                                            BusinessRepository businessRepository,
                                            ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.businessRepository = businessRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public InsightSummary save(UUID businessId, Instant from, Instant to, InsightSummaryResult result,
                              int feedbackCount, Instant latestFeedbackAt, String provider, String model) {
        var business = businessRepository.findById(businessId)
                .orElseThrow(() -> new IllegalStateException("Business disappeared during summary generation."));
        InsightSummary summary = repository.findForUpdate(businessId, from, to).orElseGet(InsightSummary::new);
        summary.setBusiness(business);
        summary.setPeriodStart(from);
        summary.setPeriodEnd(to);
        summary.setSummaryText(result.summary());
        summary.setPositiveHighlights(json(result.positiveHighlights()));
        summary.setNegativeHighlights(json(result.negativeHighlights()));
        summary.setRecurringIssues(json(result.recurringIssues()));
        summary.setRecommendations(json(result.recommendations()));
        summary.setFeedbackCount(feedbackCount);
        summary.setLatestFeedbackAt(latestFeedbackAt);
        summary.setAiProvider(provider);
        summary.setAiModel(model);
        summary.setGeneratedAt(clock.instant());
        return repository.save(summary);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Validated insight summary could not be serialized.", exception);
        }
    }
}
