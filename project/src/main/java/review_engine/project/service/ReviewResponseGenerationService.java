package review_engine.project.service;

import org.springframework.stereotype.Service;
import review_engine.project.ai.AIService;
import review_engine.project.ai.PromptTemplateService;
import review_engine.project.ai.dto.ReviewReplyResult;
import review_engine.project.dto.response.ReviewResponseDraftResponse;
import review_engine.project.enums.ResponseTone;

import java.util.Map;
import java.util.UUID;

/**
 * Generates a proposed reply from an imported Google review; it never approves or publishes it.
 */
@Service
public class ReviewResponseGenerationService {
    private final GoogleReviewContextService reviewContext;
    private final ReviewResponseDraftPersistenceService drafts;
    private final AIService ai;
    private final PromptTemplateService prompts;

    public ReviewResponseGenerationService(GoogleReviewContextService reviewContext,
                                           ReviewResponseDraftPersistenceService drafts,
                                           AIService ai, PromptTemplateService prompts) {
        this.reviewContext = reviewContext;
        this.drafts = drafts;
        this.ai = ai;
        this.prompts = prompts;
    }

    public ReviewResponseDraftResponse generate(UUID ownerId, UUID reviewId, ResponseTone requestedTone) {
        ResponseTone tone = requestedTone == null ? ResponseTone.PROFESSIONAL : requestedTone;
        GoogleReviewContextService.ReviewContext context = reviewContext.forOwner(ownerId, reviewId);
        ReviewReplyResult result = ai.generate(prompts.googleReviewReply(),
                ai.contextJson(Map.of("businessName", context.businessName(),
                        "businessCategory", context.businessCategory(), "rating", context.rating(),
                        "reviewText", context.reviewText() == null ? "" : context.reviewText(),
                        "responseTone", tone.name())),
                ReviewReplyResult.class);
        return drafts.create(ownerId, reviewId, tone, result, ai.providerName(), ai.modelName());
    }
}
