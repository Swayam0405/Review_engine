package review_engine.project.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import review_engine.project.dto.response.DeliveryDetailsResponse;
import review_engine.project.entity.FeedbackRequest;
import review_engine.project.enums.FeedbackOutboxStatus;
import review_engine.project.enums.FeedbackRequestStatus;
import review_engine.project.exception.ApiException;
import review_engine.project.repository.FeedbackOutboxRepository;
import review_engine.project.repository.FeedbackRequestRepository;

import java.time.Clock;
import java.util.UUID;

/**
 * Releases minimum WhatsApp delivery data only to the authenticated internal workflow.
 */
@Service
public class FeedbackDeliveryDetailsService {
    private final FeedbackOutboxRepository outboxRepository;
    private final FeedbackRequestRepository requestRepository;
    private final FeedbackRequestService requestService;
    private final PhoneNumberSecurityService phoneSecurity;
    private final Clock clock;

    public FeedbackDeliveryDetailsService(FeedbackOutboxRepository outboxRepository,
                                          FeedbackRequestRepository requestRepository,
                                          FeedbackRequestService requestService,
                                          PhoneNumberSecurityService phoneSecurity, Clock clock) {
        this.outboxRepository = outboxRepository;
        this.requestRepository = requestRepository;
        this.requestService = requestService;
        this.phoneSecurity = phoneSecurity;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DeliveryDetailsResponse get(UUID requestId, UUID eventId) {
        var event = outboxRepository.findByIdAndAggregateId(eventId, requestId)
                .filter(row -> (row.getStatus() == FeedbackOutboxStatus.PROCESSING
                        || row.getStatus() == FeedbackOutboxStatus.PROCESSED)
                        && "FEEDBACK_SCHEDULE".equals(row.getEventType()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Delivery event was not found."));
        FeedbackRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Feedback request was not found."));
        if (!event.getId().equals(request.getDeliveryEventId())) {
            throw new ApiException(HttpStatus.GONE, "Delivery event has been superseded.");
        }
        if (request.getStatus() == FeedbackRequestStatus.CANCELLED
                || request.getStatus() == FeedbackRequestStatus.COMPLETED
                || request.getStatus() == FeedbackRequestStatus.EXPIRED
                || !request.getExpiresAt().isAfter(clock.instant())) {
            throw new ApiException(HttpStatus.GONE, "Feedback request is no longer available for delivery.");
        }
        return new DeliveryDetailsResponse(event.getId(), request.getId(), request.getScheduledAt(),
                request.getBusiness().getName(), phoneSecurity.decrypt(request.getCustomer().getPhoneEncrypted()),
                requestService.feedbackLink(request),
                "Hello! We would appreciate your feedback about your recent visit. " + requestService.feedbackLink(request));
    }
}
