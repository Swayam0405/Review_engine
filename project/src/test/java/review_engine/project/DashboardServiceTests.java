package review_engine.project;

import org.junit.jupiter.api.Test;
import review_engine.project.config.DashboardProperties;
import review_engine.project.repository.BusinessInsightRepository;
import review_engine.project.service.BusinessInsightService;
import review_engine.project.service.BusinessScopeService;
import review_engine.project.service.DashboardService;
import review_engine.project.entity.Business;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardServiceTests {
    @Test
    void overviewSeparatesRatingMetricsFromAnalysisCoverageAndUsesOnlyAnalyzedForSentiment() {
        UUID ownerId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        Instant to = Instant.parse("2026-11-01T00:00:00Z");
        Instant from = to.minusSeconds(30 * 86400L);
        Business business = new Business();
        business.setId(businessId);
        BusinessScopeService scope = mock(BusinessScopeService.class);
        BusinessInsightRepository repository = mock(BusinessInsightRepository.class);
        BusinessInsightService insights = mock(BusinessInsightService.class);
        when(scope.requireBusiness(ownerId)).thenReturn(business);
        when(insights.resolvePeriod(null, null)).thenReturn(new BusinessInsightService.Period(from, to));
        when(repository.overview(businessId, from, to)).thenReturn(
                new BusinessInsightRepository.OverviewRow(3, 4.0, 1, 0, 0, 1, 1,
                        1, 0, 1, 0, 2, 1, 0));
        when(repository.overview(businessId, from.minusSeconds(to.getEpochSecond() - from.getEpochSecond()), from))
                .thenReturn(new BusinessInsightRepository.OverviewRow(0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0));

        var result = new DashboardService(scope, repository, insights, new DashboardProperties())
                .overview(ownerId, null, null);

        assertEquals(3, result.feedbackMetrics().totalCompletedFeedback());
        assertEquals(4.0, result.feedbackMetrics().averageRating());
        assertEquals(33.33, result.ratingDistribution().get(1).percentage());
        assertEquals(50.0, result.sentimentDistribution().get("POSITIVE").percentage());
        assertEquals(2, result.analysisCoverage().analyzed());
        assertEquals(1, result.analysisCoverage().pending());
        assertNull(result.comparison());
    }

    @Test
    void emptyOverviewReturnsZeroPercentagesWithoutDivisionByZero() {
        UUID ownerId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        Instant to = Instant.parse("2026-11-01T00:00:00Z");
        Instant from = to.minusSeconds(30 * 86400L);
        Business business = new Business();
        business.setId(businessId);
        BusinessScopeService scope = mock(BusinessScopeService.class);
        BusinessInsightRepository repository = mock(BusinessInsightRepository.class);
        BusinessInsightService insights = mock(BusinessInsightService.class);
        when(scope.requireBusiness(ownerId)).thenReturn(business);
        when(insights.resolvePeriod(null, null)).thenReturn(new BusinessInsightService.Period(from, to));
        var empty = new BusinessInsightRepository.OverviewRow(0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0);
        when(repository.overview(businessId, from, to)).thenReturn(empty);
        when(repository.overview(businessId, from.minusSeconds(30 * 86400L), from)).thenReturn(empty);

        var result = new DashboardService(scope, repository, insights, new DashboardProperties())
                .overview(ownerId, null, null);

        assertEquals(0.0, result.ratingDistribution().get(5).percentage());
        assertEquals(0.0, result.sentimentDistribution().get("MIXED").percentage());
        assertEquals(0.0, result.analysisCoverage().analyzedPercentage());
    }

    @Test
    void weeklyTrendsUseUtcMondayBucketsAndReturnTheAnalysisCoverage() {
        UUID ownerId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        Instant from = Instant.parse("2026-10-26T00:00:00Z");
        Instant to = Instant.parse("2026-11-02T00:00:00Z");
        Business business = new Business();
        business.setId(businessId);
        BusinessScopeService scope = mock(BusinessScopeService.class);
        BusinessInsightRepository repository = mock(BusinessInsightRepository.class);
        BusinessInsightService insights = mock(BusinessInsightService.class);
        when(scope.requireBusiness(ownerId)).thenReturn(business);
        when(insights.resolvePeriod(from, to)).thenReturn(new BusinessInsightService.Period(from, to));
        when(repository.trends(businessId, from, to, "week")).thenReturn(List.of(
                new BusinessInsightRepository.TrendRow(from, 2, 4.5, 1, 0, 1, 0, 2, 0, 0)));

        var result = new DashboardService(scope, repository, insights, new DashboardProperties())
                .trends(ownerId, from, to, review_engine.project.dto.response.DashboardTrendsResponse.Interval.WEEKLY);

        assertEquals(1, result.points().size());
        assertEquals(2, result.points().get(0).feedbackCount());
        assertEquals(2, result.points().get(0).analysisCoverage().analyzed());
        assertEquals(from, result.points().get(0).periodStart());
    }

    @Test
    void recurringIssuesRequireDistinctFeedbackThresholdAndSupportSeverityFiltering() {
        UUID ownerId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        Instant from = Instant.parse("2026-10-01T00:00:00Z");
        Instant to = Instant.parse("2026-11-01T00:00:00Z");
        Business business = new Business();
        business.setId(businessId);
        BusinessScopeService scope = mock(BusinessScopeService.class);
        BusinessInsightRepository repository = mock(BusinessInsightRepository.class);
        BusinessInsightService insights = mock(BusinessInsightService.class);
        when(scope.requireBusiness(ownerId)).thenReturn(business);
        when(insights.resolvePeriod(from, to)).thenReturn(new BusinessInsightService.Period(from, to));
        when(repository.issues(businessId, from, to, 2, "HIGH", 10)).thenReturn(List.of(
                new BusinessInsightRepository.IssueRow("recurring", "Repeated reports", 2, 0, 1, 1, "service",
                        from, to, 0, 0, 2, 0)));

        var result = new DashboardService(scope, repository, insights, new DashboardProperties())
                .issues(ownerId, from, to, 10, "HIGH");

        assertEquals(1, result.size());
        assertEquals("recurring", result.get(0).issue());
        assertEquals(2, result.get(0).distinctFeedbackReports());
        assertEquals(from, result.get(0).firstReportedAt());
        assertEquals(to, result.get(0).mostRecentlyReportedAt());
        verify(repository).issues(businessId, from, to, 2, "HIGH", 10);
    }
}
