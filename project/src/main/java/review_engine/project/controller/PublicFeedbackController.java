package review_engine.project.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import review_engine.project.dto.request.CustomerMessageRequest;
import review_engine.project.dto.request.FeedbackSubmissionRequest;
import review_engine.project.dto.response.ApiResponse;
import review_engine.project.dto.response.FeedbackConfirmationResponse;
import review_engine.project.dto.response.FeedbackConversationResponse;
import review_engine.project.dto.response.PublicFeedbackPageResponse;
import review_engine.project.service.FeedbackConversationService;
import review_engine.project.service.FeedbackSubmissionService;

/**
 * Public customer APIs authorized exclusively by an opaque, expiring feedback token.
 */
@RestController
@RequestMapping("/api/public/feedback/{token}")
public class PublicFeedbackController {
    private final FeedbackConversationService conversations;
    private final FeedbackSubmissionService submissions;

    public PublicFeedbackController(FeedbackConversationService conversations,
                                    FeedbackSubmissionService submissions) {
        this.conversations = conversations;
        this.submissions = submissions;
    }

    @GetMapping
    public ApiResponse<PublicFeedbackPageResponse> validate(@PathVariable String token) {
        return ApiResponse.success("Feedback link validated.", conversations.validateLink(token));
    }

    @PostMapping("/conversation")
    public ApiResponse<FeedbackConversationResponse> start(@PathVariable String token) {
        return ApiResponse.success("Feedback conversation started.", conversations.startOrResume(token));
    }

    @GetMapping("/conversation")
    public ApiResponse<FeedbackConversationResponse> getConversation(@PathVariable String token) {
        return ApiResponse.success("Feedback conversation retrieved.", conversations.get(token));
    }

    @PostMapping("/conversation/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FeedbackConversationResponse> message(@PathVariable String token,
                                                            @Valid @RequestBody CustomerMessageRequest request) {
        return ApiResponse.success("Message received.",
                conversations.addCustomerMessage(token, request.content()));
    }

    @PostMapping("/submit")
    public ApiResponse<FeedbackConfirmationResponse> submit(@PathVariable String token,
                                                            @Valid @RequestBody FeedbackSubmissionRequest request) {
        return ApiResponse.success("Feedback received.", submissions.submit(token, request));
    }
}
