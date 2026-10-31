package review_engine.project.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import review_engine.project.dto.response.ApiResponse;
import review_engine.project.dto.response.InsightSummaryResponse;
import review_engine.project.dto.response.InsightsOverviewResponse;
import review_engine.project.dto.response.IssueInsightResponse;
import review_engine.project.dto.response.TopicInsightResponse;
import review_engine.project.dto.response.TrendInsightResponse;
import review_engine.project.service.BusinessInsightService;
import review_engine.project.service.BusinessScopeService;

import java.time.Instant;
import java.util.List;

/**
 * Exposes deterministic metrics and cached, evidence-grounded summaries to the owning business.
 */
@RestController
@RequestMapping("/api/insights")
public class BusinessInsightController {
    private final BusinessScopeService businessScope;
    private final BusinessInsightService insights;

    public BusinessInsightController(BusinessScopeService businessScope, BusinessInsightService insights) {
        this.businessScope = businessScope;
        this.insights = insights;
    }

    @GetMapping("/overview")
    public ApiResponse<InsightsOverviewResponse> overview(Authentication authentication,
                                                          @RequestParam(required = false) Instant from,
                                                          @RequestParam(required = false) Instant to) {
        return ApiResponse.success("Feedback overview retrieved.",
                insights.overview(userId(authentication), from, to));
    }

    @GetMapping("/topics")
    public ApiResponse<List<TopicInsightResponse>> topics(Authentication authentication,
                                                          @RequestParam(required = false) Instant from,
                                                          @RequestParam(required = false) Instant to) {
        return ApiResponse.success("Feedback topics retrieved.",
                insights.topics(userId(authentication), from, to));
    }

    @GetMapping("/issues")
    public ApiResponse<List<IssueInsightResponse>> issues(Authentication authentication,
                                                          @RequestParam(required = false) Instant from,
                                                          @RequestParam(required = false) Instant to) {
        return ApiResponse.success("Feedback issues retrieved.",
                insights.issues(userId(authentication), from, to));
    }

    @GetMapping("/trends")
    public ApiResponse<TrendInsightResponse> trends(Authentication authentication,
                                                    @RequestParam(required = false) Instant from,
                                                    @RequestParam(required = false) Instant to) {
        return ApiResponse.success("Feedback trends retrieved.",
                insights.trends(userId(authentication), from, to));
    }

    @GetMapping("/summary")
    public ApiResponse<InsightSummaryResponse> summary(Authentication authentication,
                                                       @RequestParam(required = false) Instant from,
                                                       @RequestParam(required = false) Instant to) {
        return ApiResponse.success("Feedback summary retrieved.",
                insights.summary(userId(authentication), from, to));
    }

    @PostMapping("/summary/generate")
    public ApiResponse<InsightSummaryResponse> generate(Authentication authentication,
                                                        @RequestParam(required = false) Instant from,
                                                        @RequestParam(required = false) Instant to,
                                                        @RequestParam(defaultValue = "false") boolean force) {
        return ApiResponse.success("Feedback summary generated.",
                insights.generateSummary(userId(authentication), from, to, force));
    }

    private java.util.UUID userId(Authentication authentication) {
        return businessScope.authenticatedUserId(authentication);
    }
}
