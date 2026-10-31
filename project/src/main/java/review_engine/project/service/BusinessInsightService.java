package review_engine.project.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import review_engine.project.ai.AIProviderException;
import review_engine.project.ai.AIService;
import review_engine.project.ai.PromptTemplateService;
import review_engine.project.ai.dto.InsightSummaryResult;
import review_engine.project.config.AIProperties;
import review_engine.project.dto.response.InsightSummaryResponse;
import review_engine.project.dto.response.InsightsOverviewResponse;
import review_engine.project.dto.response.IssueInsightResponse;
import review_engine.project.dto.response.TopicInsightResponse;
import review_engine.project.dto.response.TrendInsightResponse;
import review_engine.project.entity.InsightSummary;
import review_engine.project.exception.ApiException;
import review_engine.project.repository.BusinessInsightRepository;
import review_engine.project.repository.FeedbackAnalysisRepository;
import review_engine.project.repository.FeedbackRepository;
import review_engine.project.repository.InsightSummaryRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Serves deterministic business-scoped metrics and caches grounded AI interpretations.
 */
@Service
public class BusinessInsightService {
    private static final Duration MAX_PERIOD = Duration.ofDays(366);
    private static final Duration SUMMARY_COOLDOWN = Duration.ofMinutes(15);
    private final BusinessScopeService businessScope;
    private final BusinessInsightRepository insightRepository;
    private final FeedbackAnalysisRepository analysisRepository;
    private final FeedbackRepository feedbackRepository;
    private final InsightSummaryRepository summaryRepository;
    private final InsightSummaryPersistenceService summaryPersistence;
    private final AIService ai;
    private final PromptTemplateService prompts;
    private final AIProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public BusinessInsightService(BusinessScopeService businessScope,
                                  BusinessInsightRepository insightRepository,
                                  FeedbackAnalysisRepository analysisRepository,
                                  FeedbackRepository feedbackRepository,
                                  InsightSummaryRepository summaryRepository,
                                  InsightSummaryPersistenceService summaryPersistence,
                                  AIService ai, PromptTemplateService prompts, AIProperties properties,
                                  ObjectMapper objectMapper, Clock clock) {
        this.businessScope = businessScope;
        this.insightRepository = insightRepository;
        this.analysisRepository = analysisRepository;
        this.feedbackRepository = feedbackRepository;
        this.summaryRepository = summaryRepository;
        this.summaryPersistence = summaryPersistence;
        this.ai = ai;
        this.prompts = prompts;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public InsightsOverviewResponse overview(UUID ownerId, Instant from, Instant to) {
        Period period = period(from, to);
        var row = insightRepository.overview(businessId(ownerId), period.from(), period.to());
        return new InsightsOverviewResponse(period.from(), period.to(), row.total(), row.averageRating(),
                Map.of(1, row.rating1(), 2, row.rating2(), 3, row.rating3(), 4, row.rating4(), 5, row.rating5()),
                Map.of("POSITIVE", row.positive(), "NEUTRAL", row.neutral(),
                        "NEGATIVE", row.negative(), "MIXED", row.mixed()));
    }

    @Transactional(readOnly = true)
    public List<TopicInsightResponse> topics(UUID ownerId, Instant from, Instant to) {
        Period period = period(from, to);
        return insightRepository.topics(businessId(ownerId), period.from(), period.to()).stream()
                .map(row -> new TopicInsightResponse(row.name(), row.mentions(),
                        Map.of("POSITIVE", row.positive(), "NEUTRAL", row.neutral(),
                                "NEGATIVE", row.negative(), "MIXED", row.mixed()),
                        period.from(), period.to()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<IssueInsightResponse> issues(UUID ownerId, Instant from, Instant to) {
        Period period = period(from, to);
        return insightRepository.issues(businessId(ownerId), period.from(), period.to()).stream()
                .map(row -> new IssueInsightResponse(row.name(), row.description(), row.frequency(),
                        Map.of("LOW", row.low(), "MEDIUM", row.medium(), "HIGH", row.high()),
                        row.relatedTopics(), period.from(), period.to()))
                .toList();
    }

    @Transactional(readOnly = true)
    public TrendInsightResponse trends(UUID ownerId, Instant from, Instant to) {
        Period period = period(from, to);
        var points = insightRepository.trends(businessId(ownerId), period.from(), period.to()).stream()
                .map(row -> new TrendInsightResponse.TrendPoint(row.day(), row.feedbackCount(), row.averageRating(),
                        Map.of("POSITIVE", row.positive(), "NEUTRAL", row.neutral(),
                                "NEGATIVE", row.negative(), "MIXED", row.mixed())))
                .toList();
        return new TrendInsightResponse(period.from(), period.to(), points);
    }

    @Transactional(readOnly = true)
    public InsightSummaryResponse summary(UUID ownerId, Instant from, Instant to) {
        Period period = period(from, to);
        UUID businessId = businessId(ownerId);
        InsightSummary result = summaryRepository.findByBusinessIdAndPeriodStartAndPeriodEnd(
                        businessId, period.from(), period.to())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No summary exists for this period."));
        return response(result);
    }

    public InsightSummaryResponse generateSummary(UUID ownerId, Instant from, Instant to, boolean force) {
        Period period = period(from, to);
        UUID businessId = businessId(ownerId);
        var existing = summaryRepository.findByBusinessIdAndPeriodStartAndPeriodEnd(
                businessId, period.from(), period.to());
        if (!force && existing.isPresent()
                && existing.get().getGeneratedAt().isAfter(clock.instant().minus(SUMMARY_COOLDOWN))) {
            return response(existing.get());
        }
        Map<String, Object> context = summaryContext(businessId, period);
        try {
            InsightSummaryResult result = ai.generate(prompts.businessSummary(), ai.contextJson(context),
                    InsightSummaryResult.class);
            int feedbackCount = ((Number) context.get("feedbackCount")).intValue();
            Instant latestFeedbackAt = (Instant) context.get("latestFeedbackAt");
            InsightSummary saved = summaryPersistence.save(businessId, period.from(), period.to(), result,
                    feedbackCount, latestFeedbackAt, ai.providerName(), ai.modelName());
            return response(saved);
        } catch (AIProviderException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI summary generation is unavailable.");
        }
    }

    private Map<String, Object> summaryContext(UUID businessId, Period period) {
        long analyzedCount = analysisRepository.countByBusinessIdAndAnalyzedAtGreaterThanEqualAndAnalyzedAtLessThan(
                businessId, period.from(), period.to());
        if (analyzedCount < 3) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "At least three completed feedback analyses are required for an AI summary.");
        }
        List<BusinessInsightRepository.FeedbackSample> samples = insightRepository.summarySamples(
                businessId, period.from(), period.to(), Math.min(properties.getMaxAnalysisBatch(), 50));
        if (samples.size() < 3) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "At least three completed feedback analyses are required for an AI summary.");
        }
        var overview = insightRepository.overview(businessId, period.from(), period.to());
        var topics = insightRepository.topics(businessId, period.from(), period.to()).stream().limit(20).toList();
        var issues = insightRepository.issues(businessId, period.from(), period.to()).stream().limit(20).toList();
        var latestFeedback = feedbackRepository.findFirstByBusinessIdAndSubmittedAtGreaterThanEqualAndSubmittedAtLessThanOrderBySubmittedAtDesc(
                businessId, period.from(), period.to()).map(feedback -> feedback.getSubmittedAt()).orElse(null);
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("feedbackCount", overview.total());
        context.put("latestFeedbackAt", latestFeedback);
        context.put("deterministicMetrics", overview);
        context.put("topics", topics);
        context.put("issues", issues);
        context.put("analyzedFeedbackSamples", samples);
        return context;
    }

    private UUID businessId(UUID ownerId) {
        return businessScope.requireBusiness(ownerId).getId();
    }

    private Period period(Instant from, Instant to) {
        Instant end = to == null ? clock.instant() : to;
        Instant start = from == null ? end.minus(Duration.ofDays(30)) : from;
        if (!start.isBefore(end) || Duration.between(start, end).compareTo(MAX_PERIOD) > 0
                || start.isAfter(clock.instant().plusSeconds(300))) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Insight range must be a positive interval no longer than 366 days.");
        }
        return new Period(start, end);
    }

    private InsightSummaryResponse response(InsightSummary summary) {
        return new InsightSummaryResponse(summary.getId(), summary.getPeriodStart(), summary.getPeriodEnd(),
                summary.getSummaryText(), parse(summary.getPositiveHighlights()), parse(summary.getNegativeHighlights()),
                parse(summary.getRecurringIssues()), parse(summary.getRecommendations()), summary.getFeedbackCount(),
                summary.getLatestFeedbackAt(), summary.getGeneratedAt());
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Persisted insight JSON is invalid.", exception);
        }
    }

    private record Period(Instant from, Instant to) {
    }
}
