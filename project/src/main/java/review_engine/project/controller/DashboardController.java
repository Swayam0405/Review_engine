package review_engine.project.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import review_engine.project.config.DashboardProperties;
import review_engine.project.dto.response.ApiResponse;
import review_engine.project.dto.response.DashboardActivityResponse;
import review_engine.project.dto.response.DashboardAspectResponse;
import review_engine.project.dto.response.DashboardHighlightsResponse;
import review_engine.project.dto.response.DashboardIssueResponse;
import review_engine.project.dto.response.DashboardOverviewResponse;
import review_engine.project.dto.response.DashboardSummaryResponse;
import review_engine.project.dto.response.DashboardTopicResponse;
import review_engine.project.dto.response.DashboardTrendsResponse;
import review_engine.project.service.BusinessScopeService;
import review_engine.project.service.DashboardService;

import java.time.Instant;
import java.util.List;

/**
 * Authenticated dashboard APIs; business scope is always derived from the validated principal.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final BusinessScopeService businessScope;
    private final DashboardService dashboard;
    private final DashboardProperties properties;

    public DashboardController(BusinessScopeService businessScope, DashboardService dashboard,
                               DashboardProperties properties) {
        this.businessScope = businessScope;
        this.dashboard = dashboard;
        this.properties = properties;
    }

    @GetMapping("/overview")
    public ApiResponse<DashboardOverviewResponse> overview(Authentication authentication,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to) {
        return ApiResponse.success("Dashboard overview retrieved.",
                dashboard.overview(userId(authentication), from, to));
    }

    @GetMapping("/trends")
    public ApiResponse<DashboardTrendsResponse> trends(Authentication authentication,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "DAILY") DashboardTrendsResponse.Interval interval) {
        return ApiResponse.success("Dashboard trends retrieved.",
                dashboard.trends(userId(authentication), from, to, interval));
    }

    @GetMapping("/topics")
    public ApiResponse<List<DashboardTopicResponse>> topics(Authentication authentication,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Integer limit, @RequestParam(required = false) String sentiment) {
        return ApiResponse.success("Dashboard topics retrieved.",
                dashboard.topics(userId(authentication), from, to,
                        limit == null ? properties.getDefaultResultLimit() : limit, sentiment));
    }

    @GetMapping("/aspects")
    public ApiResponse<List<DashboardAspectResponse>> aspects(Authentication authentication,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.success("Dashboard aspects retrieved.",
                dashboard.aspects(userId(authentication), from, to,
                        limit == null ? properties.getDefaultResultLimit() : limit));
    }

    @GetMapping("/issues")
    public ApiResponse<List<DashboardIssueResponse>> issues(Authentication authentication,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Integer limit, @RequestParam(required = false) String severity) {
        return ApiResponse.success("Recurring dashboard issues retrieved.",
                dashboard.issues(userId(authentication), from, to,
                        limit == null ? properties.getDefaultResultLimit() : limit, severity));
    }

    @GetMapping("/highlights")
    public ApiResponse<DashboardHighlightsResponse> highlights(Authentication authentication,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.success("Dashboard highlights retrieved.",
                dashboard.highlights(userId(authentication), from, to,
                        limit == null ? properties.getDefaultResultLimit() : limit));
    }

    @GetMapping("/summary")
    public ApiResponse<DashboardSummaryResponse> summary(Authentication authentication,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to) {
        return ApiResponse.success("Dashboard summary retrieved.",
                dashboard.summary(userId(authentication), from, to, false, false));
    }

    @PostMapping("/summary/generate")
    public ApiResponse<DashboardSummaryResponse> generateSummary(Authentication authentication,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "false") boolean force) {
        return ApiResponse.success("Dashboard summary generated.",
                dashboard.summary(userId(authentication), from, to, true, force));
    }

    @GetMapping("/activity")
    public ApiResponse<DashboardActivityResponse> activity(Authentication authentication,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to) {
        return ApiResponse.success("Dashboard activity retrieved.",
                dashboard.activity(userId(authentication), from, to));
    }

    private java.util.UUID userId(Authentication authentication) {
        return businessScope.authenticatedUserId(authentication);
    }
}
