package review_engine.project.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import review_engine.project.exception.ApiException;
import review_engine.project.repository.GoogleReviewRepository;

import java.util.UUID;

/**
 * Loads only necessary, business-authorized Google review fields before an external AI request.
 */
@Service
public class GoogleReviewContextService {
    private final GoogleReviewRepository reviewRepository;
    private final BusinessScopeService businessScope;

    public GoogleReviewContextService(GoogleReviewRepository reviewRepository, BusinessScopeService businessScope) {
        this.reviewRepository = reviewRepository;
        this.businessScope = businessScope;
    }

    @Transactional(readOnly = true)
    public ReviewContext forOwner(UUID ownerId, UUID reviewId) {
        UUID businessId = businessScope.requireBusiness(ownerId).getId();
        var review = reviewRepository.findByIdAndBusinessId(reviewId, businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Google review was not found."));
        return new ReviewContext(review.getId(), review.getBusiness().getName(),
                review.getBusiness().getCategory(), review.getRating(), review.getReviewText(),
                review.getReviewedAt());
    }

    public record ReviewContext(UUID reviewId, String businessName, String businessCategory, int rating,
                                String reviewText, java.time.Instant reviewedAt) {
    }
}
