package review_engine.project.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import review_engine.project.ai.dto.ReviewReplyResult;
import review_engine.project.dto.response.PageResponse;
import review_engine.project.dto.response.ReviewResponseDraftResponse;
import review_engine.project.entity.ReviewResponseDraft;
import review_engine.project.enums.ReviewResponseDraftStatus;
import review_engine.project.enums.ResponseTone;
import review_engine.project.exception.ApiException;
import review_engine.project.repository.GoogleReviewRepository;
import review_engine.project.repository.ReviewResponseDraftRepository;
import review_engine.project.repository.UserRepository;

import java.time.Clock;
import java.util.UUID;

/**
 * Persists and enforces owner-controlled draft transitions with row locks.
 */
@Service
public class ReviewResponseDraftPersistenceService {
    private final ReviewResponseDraftRepository draftRepository;
    private final GoogleReviewRepository reviewRepository;
    private final BusinessScopeService businessScope;
    private final UserRepository userRepository;
    private final Clock clock;

    public ReviewResponseDraftPersistenceService(ReviewResponseDraftRepository draftRepository,
                                                 GoogleReviewRepository reviewRepository,
                                                 BusinessScopeService businessScope,
                                                 UserRepository userRepository, Clock clock) {
        this.draftRepository = draftRepository;
        this.reviewRepository = reviewRepository;
        this.businessScope = businessScope;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public ReviewResponseDraftResponse create(UUID ownerId, UUID reviewId, ResponseTone tone,
                                              ReviewReplyResult generated, String provider, String model) {
        UUID businessId = businessScope.requireBusiness(ownerId).getId();
        var review = reviewRepository.lockByIdAndBusinessId(reviewId, businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Google review was not found."));
        ReviewResponseDraft draft = new ReviewResponseDraft();
        draft.setBusiness(review.getBusiness());
        draft.setGoogleReview(review);
        draft.setGeneratedText(generated.responseText());
        draft.setResponseText(generated.responseText());
        draft.setTone(tone);
        draft.setStatus(ReviewResponseDraftStatus.GENERATED);
        draft.setAiProvider(provider);
        draft.setAiModel(model);
        return response(draftRepository.save(draft));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponseDraftResponse> list(UUID ownerId, UUID reviewId, int page, int size) {
        UUID businessId = businessScope.requireBusiness(ownerId).getId();
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Page must be non-negative and size must be between 1 and 100.");
        }
        if (!reviewRepository.existsByIdAndBusinessId(reviewId, businessId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Google review was not found.");
        }
        var drafts = draftRepository.findAllByGoogleReviewIdAndBusinessId(reviewId, businessId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "generatedAt")));
        return PageResponse.from(drafts.map(this::response));
    }

    @Transactional(readOnly = true)
    public ReviewResponseDraftResponse get(UUID ownerId, UUID draftId) {
        UUID businessId = businessScope.requireBusiness(ownerId).getId();
        return response(find(draftId, businessId));
    }

    @Transactional
    public ReviewResponseDraftResponse edit(UUID ownerId, UUID draftId, String text) {
        UUID businessId = businessScope.requireBusiness(ownerId).getId();
        ReviewResponseDraft draft = lock(draftId, businessId);
        requireEditable(draft);
        String normalized = normalize(text);
        draft.setResponseText(normalized);
        draft.setStatus(ReviewResponseDraftStatus.EDITED);
        return response(draft);
    }

    @Transactional
    public ReviewResponseDraftResponse approve(UUID ownerId, UUID draftId) {
        UUID businessId = businessScope.requireBusiness(ownerId).getId();
        ReviewResponseDraft draft = lock(draftId, businessId);
        requireEditable(draft);
        draft.setStatus(ReviewResponseDraftStatus.APPROVED);
        draft.setApprovedAt(clock.instant());
        draft.setApprovedBy(businessScopeUser(ownerId));
        draft.setPublishedAt(null);
        return response(draft);
    }

    @Transactional
    public ReviewResponseDraftResponse reject(UUID ownerId, UUID draftId, String reason) {
        UUID businessId = businessScope.requireBusiness(ownerId).getId();
        ReviewResponseDraft draft = lock(draftId, businessId);
        requireEditable(draft);
        draft.setStatus(ReviewResponseDraftStatus.REJECTED);
        draft.setRejectionReason(reason == null || reason.isBlank() ? null : normalize(reason));
        return response(draft);
    }

    private review_engine.project.entity.User businessScopeUser(UUID ownerId) {
        return userRepository.findById(ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Authenticated owner was not found."));
    }

    private ReviewResponseDraft find(UUID draftId, UUID businessId) {
        return draftRepository.findByIdAndBusinessId(draftId, businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Review response draft was not found."));
    }

    private ReviewResponseDraft lock(UUID draftId, UUID businessId) {
        return draftRepository.lockByIdAndBusinessId(draftId, businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Review response draft was not found."));
    }

    private void requireEditable(ReviewResponseDraft draft) {
        if (draft.getStatus() != ReviewResponseDraftStatus.GENERATED
                && draft.getStatus() != ReviewResponseDraftStatus.EDITED) {
            throw new ApiException(HttpStatus.CONFLICT, "This review response draft can no longer be changed.");
        }
    }

    private String normalize(String text) {
        String normalized = text == null ? "" : text.strip();
        if (normalized.isBlank() || normalized.length() > 2000
                || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Response text must contain 1 to 2000 valid characters.");
        }
        return normalized;
    }

    private ReviewResponseDraftResponse response(ReviewResponseDraft draft) {
        var review = draft.getGoogleReview();
        return new ReviewResponseDraftResponse(draft.getId(), review.getId(), review.getReviewerDisplayName(),
                review.getRating(), review.getReviewText(), review.getReviewedAt(), draft.getGeneratedText(),
                draft.getResponseText(), draft.getStatus(), draft.getTone(), draft.getAiProvider(),
                draft.getAiModel(), draft.getGeneratedAt(), draft.getUpdatedAt(), draft.getApprovedAt(),
                draft.getApprovedBy() == null ? null : draft.getApprovedBy().getId(),
                draft.getPublishedAt(), draft.getRejectionReason());
    }
}
