package review_engine.project.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import review_engine.project.dto.request.CreateFeedbackRequest;
import review_engine.project.dto.response.ApiResponse;
import review_engine.project.dto.response.FeedbackRequestResponse;
import review_engine.project.dto.response.PageResponse;
import review_engine.project.enums.FeedbackRequestStatus;
import review_engine.project.service.BusinessScopeService;
import review_engine.project.service.FeedbackRequestService;

import java.util.UUID;

/**
 * Owner-authenticated feedback invitation lifecycle endpoints.
 */
@RestController
@RequestMapping("/api/feedback-requests")
public class FeedbackRequestController {
    private final FeedbackRequestService service;
    private final BusinessScopeService businessScope;

    public FeedbackRequestController(FeedbackRequestService service, BusinessScopeService businessScope) {
        this.service = service;
        this.businessScope = businessScope;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FeedbackRequestResponse> create(Authentication authentication,
                                                       @Valid @RequestBody CreateFeedbackRequest request) {
        return ApiResponse.success("Feedback request created.",
                service.create(businessScope.authenticatedUserId(authentication), request));
    }

    @GetMapping
    public ApiResponse<PageResponse<FeedbackRequestResponse>> list(
            Authentication authentication,
            @RequestParam(required = false) FeedbackRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success("Feedback requests retrieved.",
                service.list(businessScope.authenticatedUserId(authentication), status, page, size));
    }

    @GetMapping("/{requestId}")
    public ApiResponse<FeedbackRequestResponse> get(Authentication authentication, @PathVariable UUID requestId) {
        return ApiResponse.success("Feedback request retrieved.",
                service.get(businessScope.authenticatedUserId(authentication), requestId));
    }

    @PatchMapping("/{requestId}/cancel")
    public ApiResponse<FeedbackRequestResponse> cancel(Authentication authentication, @PathVariable UUID requestId) {
        return ApiResponse.success("Feedback request cancelled.",
                service.cancel(businessScope.authenticatedUserId(authentication), requestId));
    }

    @PostMapping("/{requestId}/retry")
    public ApiResponse<FeedbackRequestResponse> retry(Authentication authentication, @PathVariable UUID requestId) {
        return ApiResponse.success("Feedback request queued for retry.",
                service.retry(businessScope.authenticatedUserId(authentication), requestId));
    }
}
