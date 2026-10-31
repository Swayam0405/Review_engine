package review_engine.project.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import review_engine.project.dto.request.FeedbackQuestionRequest;
import review_engine.project.dto.response.ApiResponse;
import review_engine.project.dto.response.FeedbackQuestionResponse;
import review_engine.project.dto.response.PageResponse;
import review_engine.project.service.BusinessScopeService;
import review_engine.project.service.FeedbackQuestionService;

import java.util.UUID;

/**
 * Owner-authenticated question management endpoints.
 */
@RestController
@RequestMapping("/api/feedback-questions")
public class FeedbackQuestionController {
    private final FeedbackQuestionService service;
    private final BusinessScopeService businessScope;

    public FeedbackQuestionController(FeedbackQuestionService service, BusinessScopeService businessScope) {
        this.service = service;
        this.businessScope = businessScope;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FeedbackQuestionResponse> create(Authentication authentication,
                                                        @Valid @RequestBody FeedbackQuestionRequest request) {
        return ApiResponse.success("Feedback question created.",
                service.create(businessScope.authenticatedUserId(authentication), request));
    }

    @GetMapping
    public ApiResponse<PageResponse<FeedbackQuestionResponse>> list(
            Authentication authentication,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success("Feedback questions retrieved.",
                service.list(businessScope.authenticatedUserId(authentication), active, page, size));
    }

    @GetMapping("/{questionId}")
    public ApiResponse<FeedbackQuestionResponse> get(Authentication authentication,
                                                     @PathVariable UUID questionId) {
        return ApiResponse.success("Feedback question retrieved.",
                service.get(businessScope.authenticatedUserId(authentication), questionId));
    }

    @PutMapping("/{questionId}")
    public ApiResponse<FeedbackQuestionResponse> update(Authentication authentication,
                                                        @PathVariable UUID questionId,
                                                        @Valid @RequestBody FeedbackQuestionRequest request) {
        return ApiResponse.success("Feedback question updated.",
                service.update(businessScope.authenticatedUserId(authentication), questionId, request));
    }

    @PatchMapping("/{questionId}/status")
    public ApiResponse<FeedbackQuestionResponse> status(Authentication authentication,
                                                        @PathVariable UUID questionId,
                                                        @RequestBody StatusRequest request) {
        if (request == null || request.active() == null) {
            throw new review_engine.project.exception.ApiException(HttpStatus.BAD_REQUEST,
                    "Question active status is required.");
        }
        return ApiResponse.success("Feedback question status updated.",
                service.setActive(businessScope.authenticatedUserId(authentication), questionId, request.active()));
    }

    public record StatusRequest(Boolean active) {
    }
}
