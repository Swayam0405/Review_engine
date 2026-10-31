package review_engine.project.dto.request;

import review_engine.project.enums.ResponseTone;

/**
 * Optional controlled response tone; omitted tone defaults to professional.
 */
public record GenerateReviewDraftRequest(ResponseTone tone) {
}
