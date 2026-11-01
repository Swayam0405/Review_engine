package review_engine.project.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import review_engine.project.entity.GoogleReview;
import review_engine.project.exception.ApiException;
import review_engine.project.repository.BusinessRepository;
import review_engine.project.repository.GoogleReviewRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Persistence boundary for future authorized Google Business Profile ingestion; it does not call Google.
 */
@Service
public class GoogleReviewImportService {
    private final GoogleReviewRepository reviewRepository;
    private final BusinessRepository businessRepository;
    private final Clock clock;

    public GoogleReviewImportService(GoogleReviewRepository reviewRepository,
                                     BusinessRepository businessRepository, Clock clock) {
        this.reviewRepository = reviewRepository;
        this.businessRepository = businessRepository;
        this.clock = clock;
    }

    @Transactional
    public GoogleReview recordImportedReview(UUID businessId, String sourceReviewId, String reviewerDisplayName,
                                            int rating, String reviewText, Instant reviewedAt) {
        if (sourceReviewId == null || sourceReviewId.isBlank() || sourceReviewId.length() > 255
                || rating < 1 || rating > 5 || reviewText != null && reviewText.length() > 10000
                || reviewedAt == null || reviewedAt.isAfter(clock.instant().plusSeconds(300))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Imported Google review data is invalid.");
        }
        var business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business was not found."));
        GoogleReview review = reviewRepository.findByBusinessIdAndSourceReviewId(businessId, sourceReviewId)
                .orElseGet(GoogleReview::new);
        review.setBusiness(business);
        review.setSourceReviewId(sourceReviewId);
        review.setReviewerDisplayName(reviewerDisplayName);
        review.setRating(rating);
        review.setReviewText(reviewText);
        review.setReviewedAt(reviewedAt);
        return reviewRepository.save(review);
    }
}
