package review_engine.project.dto.response;

import review_engine.project.enums.ReviewResponseDraftStatus;
import review_engine.project.enums.ResponseTone;

import java.time.Instant;
import java.util.UUID;

/**
 * Owner-visible draft and its original public review context; publication remains unset.
 */
public record ReviewResponseDraftResponse(UUID id, UUID googleReviewId, String reviewerDisplayName,
                                          int reviewRating, String reviewText, Instant reviewDate,
                                          String generatedText, String responseText,
                                          ReviewResponseDraftStatus status, ResponseTone tone,
                                          String aiProvider, String aiModel, Instant generatedAt,
                                          Instant updatedAt, Instant approvedAt, UUID approvedBy,
                                          Instant publishedAt, String rejectionReason) {
}
