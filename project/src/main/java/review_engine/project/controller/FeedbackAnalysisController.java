package review_engine.project.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import review_engine.project.dto.response.ApiResponse;
import review_engine.project.dto.response.FeedbackAnalysisStatusResponse;
import review_engine.project.service.BusinessScopeService;
import review_engine.project.service.FeedbackAnalysisJobService;
import review_engine.project.service.FeedbackAnalysisQueryService;

import java.util.UUID;

/**
 * Owner-scoped feedback analysis status and retry operations.
 */
@RestController
@RequestMapping("/api/feedback/{feedbackId}/analysis")
public class FeedbackAnalysisController {
    private final BusinessScopeService businessScope;
    private final FeedbackAnalysisQueryService queryService;
    private final FeedbackAnalysisJobService jobService;

    public FeedbackAnalysisController(BusinessScopeService businessScope,
                                      FeedbackAnalysisQueryService queryService,
                                      FeedbackAnalysisJobService jobService) {
        this.businessScope = businessScope;
        this.queryService = queryService;
        this.jobService = jobService;
    }

    @GetMapping
    public ApiResponse<FeedbackAnalysisStatusResponse> get(Authentication authentication,
                                                           @PathVariable UUID feedbackId) {
        return ApiResponse.success("Feedback analysis status retrieved.",
                queryService.get(businessScope.authenticatedUserId(authentication), feedbackId));
    }

    @PostMapping("/retry")
    public ApiResponse<Void> retry(Authentication authentication, @PathVariable UUID feedbackId) {
        jobService.retry(businessScope.authenticatedUserId(authentication), feedbackId);
        return ApiResponse.success("Feedback analysis retry queued.", null);
    }
}
