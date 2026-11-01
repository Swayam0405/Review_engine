package review_engine.project.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import review_engine.project.ai.dto.AnalysisSentiment;
import review_engine.project.config.DashboardProperties;
import review_engine.project.dto.response.DashboardActivityResponse;
import review_engine.project.dto.response.DashboardAspectResponse;
import review_engine.project.dto.response.DashboardHighlightsResponse;
import review_engine.project.dto.response.DashboardIssueResponse;
import review_engine.project.dto.response.DashboardOverviewResponse;
import review_engine.project.dto.response.DashboardSummaryResponse;
import review_engine.project.dto.response.DashboardTopicResponse;
import review_engine.project.dto.response.DashboardTrendsResponse;
import review_engine.project.exception.ApiException;
import review_engine.project.repository.BusinessInsightRepository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Composes business-scoped dashboard views from deterministic SQL aggregates and persisted AI results.
 */
@Service
public class DashboardService {
    private static final List<String> SENTIMENTS = List.of("POSITIVE", "NEUTRAL", "NEGATIVE", "MIXED");
    private final BusinessScopeService businessScope;
    private final BusinessInsightRepository repository;
    private final BusinessInsightService insights;
    private final DashboardProperties properties;

    public DashboardService(BusinessScopeService businessScope, BusinessInsightRepository repository,
                           BusinessInsightService insights, DashboardProperties properties) {
        this.businessScope = businessScope;
        this.repository = repository;
        this.insights = insights;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public DashboardOverviewResponse overview(UUID ownerId, Instant from, Instant to) {
        BusinessInsightService.Period period = insights.resolvePeriod(from, to);
        UUID businessId = businessId(ownerId);
        var current = repository.overview(businessId, period.from(), period.to());
        var previousFrom = period.from().minus(java.time.Duration.between(period.from(), period.to()));
        var previous = repository.overview(businessId, previousFrom, period.from());
        DashboardOverviewResponse.PeriodComparison comparison = previous.total() == 0 ? null
                : new DashboardOverviewResponse.PeriodComparison(previousFrom, period.from(), previous.total(),
                current.total(), percentage(current.total(), previous.total()));
        return overviewResponse(period.from(), period.to(), current, comparison);
    }

    @Transactional(readOnly = true)
    public DashboardTrendsResponse trends(UUID ownerId, Instant from, Instant to,
                                          DashboardTrendsResponse.Interval interval) {
        var period = insights.resolvePeriod(from, to);
        UUID businessId = businessId(ownerId);
        String sqlInterval = switch (interval) {
            case DAILY -> "day";
            case WEEKLY -> "week";
            case MONTHLY -> "month";
        };
        Map<Instant, BusinessInsightRepository.TrendRow> rows = repository
                .trends(businessId, period.from(), period.to(), sqlInterval).stream()
                .collect(Collectors.toMap(BusinessInsightRepository.TrendRow::day, Function.identity()));
        List<DashboardTrendsResponse.Point> points = new ArrayList<>();
        Instant bucket = bucketStart(period.from(), interval);
        while (bucket.isBefore(period.to())) {
            Instant next = nextBucket(bucket, interval);
            Instant visibleStart = bucket.isBefore(period.from()) ? period.from() : bucket;
            Instant visibleEnd = next.isAfter(period.to()) ? period.to() : next;
            BusinessInsightRepository.TrendRow row = rows.get(bucket);
            long total = row == null ? 0 : row.feedbackCount();
            long analyzed = row == null ? 0 : row.analyzed();
            Map<String, Long> sentiments = sentimentCounts(row == null ? 0 : row.positive(),
                    row == null ? 0 : row.neutral(), row == null ? 0 : row.negative(),
                    row == null ? 0 : row.mixed());
            points.add(new DashboardTrendsResponse.Point(visibleStart, visibleEnd, total,
                    row == null ? 0 : row.averageRating(), sentiments,
                    coverage(total, analyzed, row == null ? 0 : row.analysisPending(),
                            row == null ? 0 : row.analysisFailed())));
            bucket = next;
        }
        return new DashboardTrendsResponse(period.from(), period.to(), interval, points);
    }

    @Transactional(readOnly = true)
    public List<DashboardTopicResponse> topics(UUID ownerId, Instant from, Instant to, int limit, String sentiment) {
        validateLimit(limit);
        String selectedSentiment = normalizeSentiment(sentiment);
        var period = insights.resolvePeriod(from, to);
        UUID businessId = businessId(ownerId);
        long analyzed = repository.overview(businessId, period.from(), period.to()).analyzed();
        return repository.topics(businessId, period.from(), period.to(), selectedSentiment, limit).stream()
                .map(row -> new DashboardTopicResponse(row.name(), row.mentions(), row.positive(), row.neutral(),
                        row.negative(), row.mixed(), percentage(row.mentions(), analyzed),
                        sentimentCounts(row.positive(), row.neutral(), row.negative(), row.mixed()),
                        period.from(), period.to()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DashboardAspectResponse> aspects(UUID ownerId, Instant from, Instant to, int limit) {
        validateLimit(limit);
        var period = insights.resolvePeriod(from, to);
        UUID businessId = businessId(ownerId);
        return repository.aspects(businessId, period.from(), period.to(), limit).stream()
                .map(row -> new DashboardAspectResponse(row.name(), row.mentions(),
                        sentimentCounts(row.positive(), row.neutral(), row.negative(), row.mixed()),
                        row.averageConfidence(), period.from(), period.to()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DashboardIssueResponse> issues(UUID ownerId, Instant from, Instant to, int limit, String severity) {
        validateLimit(limit);
        String selectedSeverity = normalizeSeverity(severity);
        var period = insights.resolvePeriod(from, to);
        UUID businessId = businessId(ownerId);
        return repository.issues(businessId, period.from(), period.to(),
                        properties.getRecurringIssueMinFeedbackCount(), selectedSeverity, limit).stream()
                .map(row -> new DashboardIssueResponse(row.name(), row.description(), row.frequency(),
                        Map.of("LOW", row.low(), "MEDIUM", row.medium(), "HIGH", row.high()),
                        row.relatedTopics(), row.firstReportedAt(), row.lastReportedAt(),
                        sentimentCounts(row.positive(), row.neutral(), row.negative(), row.mixed())))
                .toList();
    }

    @Transactional(readOnly = true)
    public DashboardHighlightsResponse highlights(UUID ownerId, Instant from, Instant to, int limit) {
        validateLimit(limit);
        var period = insights.resolvePeriod(from, to);
        List<DashboardHighlightsResponse.Highlight> positive = new ArrayList<>();
        List<DashboardHighlightsResponse.Highlight> negative = new ArrayList<>();
        repository.highlights(businessId(ownerId), period.from(), period.to(), limit).forEach(row -> {
            DashboardHighlightsResponse.Highlight item = new DashboardHighlightsResponse.Highlight(
                    row.aspect(), row.explanation(), row.supportingFeedback(), row.sentiment());
            if ("POSITIVE".equals(row.sentiment())) {
                positive.add(item);
            } else {
                negative.add(item);
            }
        });
        return new DashboardHighlightsResponse(period.from(), period.to(), positive, negative);
    }

    public DashboardSummaryResponse summary(UUID ownerId, Instant from, Instant to, boolean generate, boolean force) {
        var period = insights.resolvePeriod(from, to);
        var result = generate
                ? insights.generateSummary(ownerId, period.from(), period.to(), force)
                : insights.summary(ownerId, period.from(), period.to());
        var metrics = repository.overview(businessId(ownerId), period.from(), period.to());
        return new DashboardSummaryResponse(result.id(), result.from(), result.to(), result.summary(),
                result.positiveHighlights(), result.negativeHighlights(), result.recurringIssues(),
                result.recommendations(), result.feedbackCount(), result.latestFeedbackAt(), result.generatedAt(),
                coverage(metrics.total(), metrics.analyzed(), metrics.analysisPending(), metrics.analysisFailed()));
    }

    @Transactional(readOnly = true)
    public DashboardActivityResponse activity(UUID ownerId, Instant from, Instant to) {
        var period = insights.resolvePeriod(from, to);
        var row = repository.activity(businessId(ownerId), period.from(), period.to());
        return new DashboardActivityResponse(period.from(), period.to(), row.totalRequests(), row.pendingRequests(),
                row.scheduledRequests(), row.sentRequests(), row.deliveredRequests(), row.failedRequests(),
                row.completedRequests(), row.expiredRequests(), row.conversationsInProgress(),
                row.conversationsCompleted(), row.analysisPending(), row.analysisFailed());
    }

    private DashboardOverviewResponse overviewResponse(Instant from, Instant to,
            BusinessInsightRepository.OverviewRow row,
            DashboardOverviewResponse.PeriodComparison comparison) {
        Map<Integer, DashboardOverviewResponse.RatingMetric> ratings = new LinkedHashMap<>();
        long[] counts = {row.rating1(), row.rating2(), row.rating3(), row.rating4(), row.rating5()};
        for (int rating = 1; rating <= 5; rating++) {
            ratings.put(rating, new DashboardOverviewResponse.RatingMetric(counts[rating - 1],
                    percentage(counts[rating - 1], row.total())));
        }
        Map<String, DashboardOverviewResponse.SentimentMetric> sentiments = new LinkedHashMap<>();
        long[] sentimentCounts = {row.positive(), row.neutral(), row.negative(), row.mixed()};
        for (int i = 0; i < SENTIMENTS.size(); i++) {
            sentiments.put(SENTIMENTS.get(i), new DashboardOverviewResponse.SentimentMetric(
                    sentimentCounts[i], percentage(sentimentCounts[i], row.analyzed())));
        }
        return new DashboardOverviewResponse(new DashboardOverviewResponse.Period(from, to),
                new DashboardOverviewResponse.FeedbackMetrics(row.total(), row.averageRating(), row.positive(),
                        row.neutral(), row.negative(), row.mixed()), ratings, sentiments,
                coverage(row.total(), row.analyzed(), row.analysisPending(), row.analysisFailed()), comparison);
    }

    private DashboardOverviewResponse.AnalysisCoverage coverage(long total, long analyzed, long pending,
                                                                  long failed) {
        return new DashboardOverviewResponse.AnalysisCoverage(total, analyzed, pending, failed,
                percentage(analyzed, total));
    }

    private Map<String, Long> sentimentCounts(long positive, long neutral, long negative, long mixed) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("POSITIVE", positive);
        counts.put("NEUTRAL", neutral);
        counts.put("NEGATIVE", negative);
        counts.put("MIXED", mixed);
        return counts;
    }

    private double percentage(long numerator, long denominator) {
        return denominator == 0 ? 0 : Math.round((numerator * 10000.0) / denominator) / 100.0;
    }

    private UUID businessId(UUID ownerId) {
        return businessScope.requireBusiness(ownerId).getId();
    }

    private void validateLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Dashboard result limit must be between 1 and 100.");
        }
    }

    private String normalizeSentiment(String sentiment) {
        if (sentiment == null || sentiment.isBlank()) {
            return null;
        }
        String normalized = sentiment.strip().toUpperCase(java.util.Locale.ROOT);
        try {
            return AnalysisSentiment.valueOf(normalized).name();
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Sentiment filter is invalid.");
        }
    }

    private String normalizeSeverity(String severity) {
        if (severity == null || severity.isBlank()) {
            return null;
        }
        String normalized = severity.strip().toUpperCase(java.util.Locale.ROOT);
        if (!List.of("LOW", "MEDIUM", "HIGH").contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Severity filter is invalid.");
        }
        return normalized;
    }

    private Instant bucketStart(Instant instant, DashboardTrendsResponse.Interval interval) {
        var utc = instant.atZone(ZoneOffset.UTC);
        return switch (interval) {
            case DAILY -> utc.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
            case WEEKLY -> utc.toLocalDate().with(java.time.DayOfWeek.MONDAY)
                    .atStartOfDay(ZoneOffset.UTC).toInstant();
            case MONTHLY -> utc.toLocalDate().withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        };
    }

    private Instant nextBucket(Instant start, DashboardTrendsResponse.Interval interval) {
        var utc = start.atZone(ZoneOffset.UTC);
        return switch (interval) {
            case DAILY -> utc.plusDays(1).toInstant();
            case WEEKLY -> utc.plusWeeks(1).toInstant();
            case MONTHLY -> utc.plusMonths(1).toInstant();
        };
    }
}
