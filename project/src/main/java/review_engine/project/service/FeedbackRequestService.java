package review_engine.project.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import review_engine.project.config.FeedbackProperties;
import review_engine.project.dto.request.CreateFeedbackRequest;
import review_engine.project.dto.response.FeedbackRequestResponse;
import review_engine.project.dto.response.PageResponse;
import review_engine.project.entity.Business;
import review_engine.project.entity.FeedbackOutboxEvent;
import review_engine.project.entity.FeedbackRequest;
import review_engine.project.entity.TransactionEntity;
import review_engine.project.enums.FeedbackOutboxStatus;
import review_engine.project.enums.FeedbackRequestStatus;
import review_engine.project.enums.TransactionStatus;
import review_engine.project.exception.ApiException;
import review_engine.project.repository.FeedbackOutboxRepository;
import review_engine.project.repository.FeedbackRequestRepository;
import review_engine.project.repository.TransactionRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Creates and manages owner-scoped invitations and their durable scheduling events.
 */
@Service
public class FeedbackRequestService {
    private final FeedbackRequestRepository requestRepository;
    private final FeedbackOutboxRepository outboxRepository;
    private final TransactionRepository transactionRepository;
    private final BusinessScopeService businessScope;
    private final FeedbackTokenService tokens;
    private final FeedbackProperties properties;
    private final Clock clock;

    public FeedbackRequestService(FeedbackRequestRepository requestRepository,
                                  FeedbackOutboxRepository outboxRepository,
                                  TransactionRepository transactionRepository,
                                  BusinessScopeService businessScope, FeedbackTokenService tokens,
                                  FeedbackProperties properties, Clock clock) {
        this.requestRepository = requestRepository;
        this.outboxRepository = outboxRepository;
        this.transactionRepository = transactionRepository;
        this.businessScope = businessScope;
        this.tokens = tokens;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public FeedbackRequestResponse create(UUID ownerId, CreateFeedbackRequest request) {
        Business business = businessScope.requireBusiness(ownerId);
        TransactionEntity transaction = transactionRepository.findByIdAndBusinessId(
                        request.transactionId(), business.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Transaction was not found."));
        if (transaction.getStatus() != TransactionStatus.COMPLETED) {
            throw new ApiException(HttpStatus.CONFLICT, "Only completed transactions can receive feedback requests.");
        }
        if (requestRepository.findByTransactionId(transaction.getId()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "A feedback request already exists for this transaction.");
        }
        Instant now = clock.instant();
        Instant scheduledAt = request.scheduledAt() == null ? now : request.scheduledAt().toInstant();
        if (scheduledAt.isBefore(now.minusSeconds(300))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Scheduled time cannot be more than five minutes in the past.");
        }
        if (properties.getTokenExpiry() == null || properties.getTokenExpiry().isZero()
                || properties.getTokenExpiry().isNegative()) {
            throw new IllegalStateException("Feedback token expiry must be positive.");
        }

        FeedbackRequest feedbackRequest = new FeedbackRequest();
        UUID requestId = UUID.randomUUID();
        feedbackRequest.setId(requestId);
        feedbackRequest.setBusiness(business);
        feedbackRequest.setTransaction(transaction);
        feedbackRequest.setCustomer(transaction.getCustomer());
        feedbackRequest.setRequestTokenHash(tokens.hash(tokens.tokenFor(requestId)));
        feedbackRequest.setStatus(FeedbackRequestStatus.PENDING);
        feedbackRequest.setScheduledAt(scheduledAt);
        feedbackRequest.setExpiresAt(now.plus(properties.getTokenExpiry()));
        UUID eventId = UUID.randomUUID();
        feedbackRequest.setDeliveryEventId(eventId);
        requestRepository.save(feedbackRequest);

        FeedbackOutboxEvent event = new FeedbackOutboxEvent();
        event.setId(eventId);
        event.setEventType("FEEDBACK_SCHEDULE");
        event.setAggregateId(requestId);
        event.setPayload("{\"eventId\":\"" + event.getId() + "\",\"feedbackRequestId\":\""
                + requestId + "\",\"scheduledAt\":\"" + scheduledAt + "\",\"eventType\":\"FEEDBACK_SCHEDULE\"}");
        event.setStatus(FeedbackOutboxStatus.PENDING);
        event.setNextAttemptAt(now);
        outboxRepository.save(event);
        return response(feedbackRequest, true);
    }

    @Transactional(readOnly = true)
    public PageResponse<FeedbackRequestResponse> list(UUID ownerId, FeedbackRequestStatus status,
                                                       int page, int size) {
        UUID businessId = businessScope.requireBusiness(ownerId).getId();
        Pageable pageable = pageable(page, size);
        Page<FeedbackRequest> requests = status == null
                ? requestRepository.findAllByBusinessId(businessId, pageable)
                : requestRepository.findAllByBusinessIdAndStatus(businessId, status, pageable);
        return PageResponse.from(requests.map(request -> response(request, false)));
    }

    @Transactional(readOnly = true)
    public FeedbackRequestResponse get(UUID ownerId, UUID requestId) {
        UUID businessId = businessScope.requireBusiness(ownerId).getId();
        return response(find(requestId, businessId), false);
    }

    @Transactional
    public FeedbackRequestResponse cancel(UUID ownerId, UUID requestId) {
        UUID businessId = businessScope.requireBusiness(ownerId).getId();
        FeedbackRequest request = requestRepository.lockByIdAndBusinessId(requestId, businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Feedback request was not found."));
        if (request.getStatus() == FeedbackRequestStatus.COMPLETED
                || request.getStatus() == FeedbackRequestStatus.CANCELLED
                || request.getStatus() == FeedbackRequestStatus.EXPIRED) {
            throw new ApiException(HttpStatus.CONFLICT, "This feedback request cannot be cancelled.");
        }
        request.setStatus(FeedbackRequestStatus.CANCELLED);
        return response(request, false);
    }

    @Transactional
    public FeedbackRequestResponse retry(UUID ownerId, UUID requestId) {
        UUID businessId = businessScope.requireBusiness(ownerId).getId();
        FeedbackRequest request = requestRepository.lockByIdAndBusinessId(requestId, businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Feedback request was not found."));
        if (request.getStatus() != FeedbackRequestStatus.FAILED || !request.getExpiresAt().isAfter(clock.instant())) {
            throw new ApiException(HttpStatus.CONFLICT, "Only an unexpired failed request can be retried.");
        }
        request.setStatus(FeedbackRequestStatus.PENDING);
        request.setFailureReason(null);
        Instant scheduledAt = clock.instant();
        request.setScheduledAt(scheduledAt);
        request.setSentAt(null);
        request.setDeliveredAt(null);
        request.setProviderMessageId(null);
        UUID eventId = UUID.randomUUID();
        request.setDeliveryEventId(eventId);
        FeedbackOutboxEvent event = new FeedbackOutboxEvent();
        event.setId(eventId);
        event.setEventType("FEEDBACK_SCHEDULE");
        event.setAggregateId(request.getId());
        event.setPayload("{\"eventId\":\"" + event.getId() + "\",\"feedbackRequestId\":\""
                + request.getId() + "\",\"scheduledAt\":\"" + scheduledAt
                + "\",\"eventType\":\"FEEDBACK_SCHEDULE\"}");
        event.setStatus(FeedbackOutboxStatus.PENDING);
        event.setNextAttemptAt(scheduledAt);
        outboxRepository.save(event);
        return response(request, false);
    }

    public FeedbackRequest requireValidToken(String token, boolean lock) {
        FeedbackRequest request = verifiedToken(token, lock);
        if (request.getStatus() == FeedbackRequestStatus.COMPLETED
                || request.getStatus() == FeedbackRequestStatus.EXPIRED
                || request.getStatus() == FeedbackRequestStatus.CANCELLED
                || request.getStatus() == FeedbackRequestStatus.FAILED) {
            throw invalidToken();
        }
        return request;
    }

    // Completed links may restore the page, while conversation and submission operations remain blocked.
    public FeedbackRequest requireValidPageToken(String token) {
        FeedbackRequest request = verifiedToken(token, false);
        if (request.getStatus() == FeedbackRequestStatus.EXPIRED
                || request.getStatus() == FeedbackRequestStatus.CANCELLED
                || request.getStatus() == FeedbackRequestStatus.FAILED) {
            throw invalidToken();
        }
        return request;
    }

    private FeedbackRequest verifiedToken(String token, boolean lock) {
        if (token == null || token.length() < 32 || token.length() > 128
                || !token.matches("[A-Za-z0-9_-]+")) {
            throw invalidToken();
        }
        String hash = tokens.hash(token);
        FeedbackRequest request = (lock ? requestRepository.findByRequestTokenHashForUpdate(hash)
                : requestRepository.findByRequestTokenHash(hash)).orElseThrow(this::invalidToken);
        if (!tokens.matches(token, request.getRequestTokenHash())) {
            throw invalidToken();
        }
        if (!request.getExpiresAt().isAfter(clock.instant())) {
            throw new ApiException(HttpStatus.GONE, "This feedback link has expired.");
        }
        return request;
    }

    public String tokenFor(FeedbackRequest request) {
        return tokens.tokenFor(request.getId());
    }

    public String feedbackLink(FeedbackRequest request) {
        return properties.getPublicBaseUrl().replaceAll("/+$", "") + "/" + tokenFor(request);
    }

    private FeedbackRequest find(UUID id, UUID businessId) {
        return requestRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Feedback request was not found."));
    }

    private Pageable pageable(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Page must be non-negative and size must be between 1 and 100.");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
    }

    private ApiException invalidToken() {
        return new ApiException(HttpStatus.NOT_FOUND, "Feedback link is invalid or unavailable.");
    }

    private FeedbackRequestResponse response(FeedbackRequest request, boolean includeLink) {
        return new FeedbackRequestResponse(request.getId(), request.getTransaction().getId(),
                request.getCustomer().getId(), request.getStatus(), request.getScheduledAt(), request.getSentAt(),
                request.getDeliveredAt(), request.getCompletedAt(), request.getExpiresAt(), request.getCreatedAt(),
                includeLink ? feedbackLink(request) : null);
    }
}
