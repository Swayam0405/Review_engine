package review_engine.project.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import review_engine.project.dto.request.EditReviewDraftRequest;
import review_engine.project.dto.request.GenerateReviewDraftRequest;
import review_engine.project.dto.request.RejectReviewDraftRequest;
import review_engine.project.dto.response.ApiResponse;
import review_engine.project.dto.response.PageResponse;
import review_engine.project.dto.response.ReviewResponseDraftResponse;
import review_engine.project.service.BusinessScopeService;
import review_engine.project.service.ReviewResponseDraftPersistenceService;
import review_engine.project.service.ReviewResponseGenerationService;

import java.util.UUID;

/**
 * Owner-authenticated Google review response generation, editing, and mandatory approval APIs.
 */
@RestController
public class ReviewResponseDraftController {
    private final BusinessScopeService businessScope;
    private final ReviewResponseGenerationService generationService;
    private final ReviewResponseDraftPersistenceService draftService;

    public ReviewResponseDraftController(BusinessScopeService businessScope,
                                         ReviewResponseGenerationService generationService,
                                         ReviewResponseDraftPersistenceService draftService) {
        this.businessScope = businessScope;
        this.generationService = generationService;
        this.draftService = draftService;
    }

    @PostMapping("/api/google-reviews/{reviewId}/response-drafts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReviewResponseDraftResponse> generate(Authentication authentication,
            @PathVariable UUID reviewId, @RequestBody(required = false) GenerateReviewDraftRequest request) {
        return ApiResponse.success("Google review response draft generated.",
                generationService.generate(userId(authentication), reviewId,
                        request == null ? null : request.tone()));
    }

    @GetMapping("/api/google-reviews/{reviewId}/response-drafts")
    public ApiResponse<PageResponse<ReviewResponseDraftResponse>> list(Authentication authentication,
            @PathVariable UUID reviewId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success("Google review response drafts retrieved.",
                draftService.list(userId(authentication), reviewId, page, size));
    }

    @GetMapping("/api/review-response-drafts/{draftId}")
    public ApiResponse<ReviewResponseDraftResponse> get(Authentication authentication,
                                                        @PathVariable UUID draftId) {
        return ApiResponse.success("Review response draft retrieved.",
                draftService.get(userId(authentication), draftId));
    }

    @PutMapping("/api/review-response-drafts/{draftId}")
    public ApiResponse<ReviewResponseDraftResponse> edit(Authentication authentication,
            @PathVariable UUID draftId, @Valid @RequestBody EditReviewDraftRequest request) {
        return ApiResponse.success("Review response draft updated.",
                draftService.edit(userId(authentication), draftId, request.responseText()));
    }

    @PostMapping("/api/review-response-drafts/{draftId}/approve")
    public ApiResponse<ReviewResponseDraftResponse> approve(Authentication authentication,
                                                            @PathVariable UUID draftId) {
        return ApiResponse.success("Review response draft approved; it has not been published.",
                draftService.approve(userId(authentication), draftId));
    }

    @PostMapping("/api/review-response-drafts/{draftId}/reject")
    public ApiResponse<ReviewResponseDraftResponse> reject(Authentication authentication,
            @PathVariable UUID draftId, @RequestBody(required = false) @Valid RejectReviewDraftRequest request) {
        return ApiResponse.success("Review response draft rejected.",
                draftService.reject(userId(authentication), draftId, request == null ? null : request.reason()));
    }

    private UUID userId(Authentication authentication) {
        return businessScope.authenticatedUserId(authentication);
    }
}
