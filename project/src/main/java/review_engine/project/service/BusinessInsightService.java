package review_engine.project.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import review_engine.project.ai.AIProviderException;
import review_engine.project.ai.AIService;
import review_engine.project.ai.PromptTemplateService;
import review_engine.project.ai.dto.InsightSummaryResult;
import review_engine.project.config.AIProperties;
import review_engine.project.config.DashboardProperties;
import review_engine.project.dto.response.InsightSummaryResponse;
import review_engine.project.dto.response.InsightsOverviewResponse;
import review_engine.project.dto.response.IssueInsightResponse;
import review_engine.project.dto.response.TopicInsightResponse;
import review_engine.project.dto.response.TrendInsightResponse;
import review_engine.project.entity.InsightSummary;
import review_engine.project.exception.ApiException;
import review_engine.project.repository.BusinessInsightRepository;
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
    private static final Duration SUMMARY_COOLDOWN = Duration.ofMinutes(15);
    private final BusinessScopeService businessScope;
    private final BusinessInsightRepository insightRepository;
    private final InsightSummaryRepository summaryRepository;
    private final InsightSummaryPersistenceService summaryPersistence;
    private final InsightSummaryGenerationLockService summaryLock;
    private final AIService ai;
    private final PromptTemplateService prompts;
    private final AIProperties properties;
    private final DashboardProperties dashboardProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public BusinessInsightService(BusinessScopeService businessScope,
                                  BusinessInsightRepository insightRepository,
                                  InsightSummaryRepository summaryRepository,
                                  InsightSummaryPersistenceService summaryPersistence,
                                  InsightSummaryGenerationLockService summaryLock,
                                  AIService ai, PromptTemplateService prompts, AIProperties properties,
                                  DashboardProperties dashboardProperties, ObjectMapper objectMapper, Clock clock) {
        this.businessScope = businessScope;
        this.insightRepository = insightRepository;
        this.summaryRepository = summaryRepository;
        this.summaryPersistence = summaryPersistence;
        this.summaryLock = summaryLock;
        this.ai = ai;
        this.prompts = prompts;
        this.properties = properties;
        this.dashboardProperties = dashboardProperties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public InsightsOverviewResponse overview(UUID ownerId, Instant from, Instant to) {
        Period period = resolvePeriod(from, to);
        var row = insightRepository.overview(businessId(ownerId), period.from(), period.to());
        return new InsightsOverviewResponse(period.from(), period.to(), row.total(), row.averageRating(),
                Map.of(1, row.rating1(), 2, row.rating2(), 3, row.rating3(), 4, row.rating4(), 5, row.rating5()),
                Map.of("POSITIVE", row.positive(), "NEUTRAL", row.neutral(),
                        "NEGATIVE", row.negative(), "MIXED", row.mixed()));
    }

    @Transactional(readOnly = true)
    public List<TopicInsightResponse> topics(UUID ownerId, Instant from, Instant to) {
        Period period = resolvePeriod(from, to);
        return insightRepository.topics(businessId(ownerId), period.from(), period.to()).stream()
                .map(row -> new TopicInsightResponse(row.name(), row.mentions(),
                        Map.of("POSITIVE", row.positive(), "NEUTRAL", row.neutral(),
                                "NEGATIVE", row.negative(), "MIXED", row.mixed()),
                        period.from(), period.to()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<IssueInsightResponse> issues(UUID ownerId, Instant from, Instant to) {
        Period period = resolvePeriod(from, to);
        return insightRepository.issues(businessId(ownerId), period.from(), period.to()).stream()
                .map(row -> new IssueInsightResponse(row.name(), row.description(), row.frequency(),
                        Map.of("LOW", row.low(), "MEDIUM", row.medium(), "HIGH", row.high()),
                        row.relatedTopics(), period.from(), period.to()))
                .toList();
    }

    @Transactional(readOnly = true)
    public TrendInsightResponse trends(UUID ownerId, Instant from, Instant to) {
        Period period = resolvePeriod(from, to);
        var points = insightRepository.trends(businessId(ownerId), period.from(), period.to()).stream()
                .map(row -> new TrendInsightResponse.TrendPoint(row.day(), row.feedbackCount(), row.averageRating(),
                        Map.of("POSITIVE", row.positive(), "NEUTRAL", row.neutral(),
                                "NEGATIVE", row.negative(), "MIXED", row.mixed())))
                .toList();
        return new TrendInsightResponse(period.from(), period.to(), points);
    }

    @Transactional(readOnly = true)
    public InsightSummaryResponse summary(UUID ownerId, Instant from, Instant to) {
        Period period = resolvePeriod(from, to);
        UUID businessId = businessId(ownerId);
        InsightSummary result = summaryRepository.findByBusinessIdAndPeriodStartAndPeriodEnd(
                        businessId, period.from(), period.to())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No summary exists for this period."));
        return response(result);
    }

    public InsightSummaryResponse generateSummary(UUID ownerId, Instant from, Instant to, boolean force) {
        Period period = resolvePeriod(from, to);
        UUID businessId = businessId(ownerId);
        var acquired = summaryLock.acquire(businessId, period.from(), period.to(), clock.instant());
        if (acquired.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "A summary for this period is already being generated.");
        }
        try {
            var existing = summaryRepository.findByBusinessIdAndPeriodStartAndPeriodEnd(
                    businessId, period.from(), period.to());
            var freshness = insightRepository.summaryFreshness(businessId, period.from(), period.to());
            if (!force && existing.isPresent()
                    && existing.get().getGeneratedAt().isAfter(clock.instant().minus(SUMMARY_COOLDOWN))
                    && existing.get().getFeedbackCount() == freshness.feedbackCount()
                    && existing.get().getAnalyzedFeedbackCount() == freshness.analyzedFeedbackCount()
                    && java.util.Objects.equals(existing.get().getLatestFeedbackAt(), freshness.latestFeedbackAt())) {
                return response(existing.get());
            }
            Map<String, Object> context = summaryContext(businessId, period);
            InsightSummaryResult result = ai.generate(prompts.businessSummary(), ai.contextJson(context),
                    InsightSummaryResult.class);
            int feedbackCount = ((Number) context.get("feedbackCount")).intValue();
            int analyzedCount = ((Number) context.get("analyzedFeedbackCount")).intValue();
            Instant latestFeedbackAt = (Instant) context.get("latestFeedbackAt");
            InsightSummary saved = summaryPersistence.save(businessId, period.from(), period.to(), result,
                    feedbackCount, analyzedCount, latestFeedbackAt, ai.providerName(), ai.modelName());
            return response(saved);
        } catch (AIProviderException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI summary generation is unavailable.");
        } finally {
            summaryLock.release(acquired.get());
        }
    }

    private Map<String, Object> summaryContext(UUID businessId, Period period) {
        var freshness = insightRepository.summaryFreshness(businessId, period.from(), period.to());
        long analyzedCount = freshness.analyzedFeedbackCount();
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
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("feedbackCount", freshness.feedbackCount());
        context.put("analyzedFeedbackCount", Math.toIntExact(analyzedCount));
        context.put("latestFeedbackAt", freshness.latestFeedbackAt());
        context.put("deterministicMetrics", overview);
        context.put("topics", topics);
        context.put("issues", issues);
        context.put("analyzedFeedbackSamples", samples);
        return context;
    }

    private UUID businessId(UUID ownerId) {
        return businessScope.requireBusiness(ownerId).getId();
    }

    public Period resolvePeriod(Instant from, Instant to) {
        Instant end = to == null ? clock.instant() : to;
        Instant start = from == null ? end.minusSeconds(dashboardProperties.getDefaultPeriodDays() * 86400L) : from;
        if (!start.isBefore(end)
                || Duration.between(start, end).compareTo(Duration.ofDays(
                        dashboardProperties.getMaxDateRangeDays())) > 0
                || start.isAfter(clock.instant().plusSeconds(300))
                || end.isAfter(clock.instant().plusSeconds(300))) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Insight range must be a positive interval within the configured maximum.");
        }
        return new Period(start, end);
    }

    private InsightSummaryResponse response(InsightSummary summary) {
        return new InsightSummaryResponse(summary.getId(), summary.getPeriodStart(), summary.getPeriodEnd(),
                summary.getSummaryText(), parse(summary.getPositiveHighlights()), parse(summary.getNegativeHighlights()),
                parse(summary.getRecurringIssues()), parse(summary.getRecommendations()), summary.getFeedbackCount(),
                summary.getAnalyzedFeedbackCount(),
                summary.getLatestFeedbackAt(), summary.getGeneratedAt());
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Persisted insight JSON is invalid.", exception);
        }
    }

    public record Period(Instant from, Instant to) {
    }
}
