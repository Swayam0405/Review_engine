package review_engine.project.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import review_engine.project.dto.request.DeliveryCallbackRequest;
import review_engine.project.entity.FeedbackRequest;
import review_engine.project.entity.FeedbackWebhookEvent;
import review_engine.project.enums.FeedbackRequestStatus;
import review_engine.project.exception.ApiException;
import review_engine.project.repository.FeedbackRequestRepository;
import review_engine.project.repository.FeedbackWebhookEventRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Authenticates delivery state transitions and records callback IDs for replay protection.
 */
@Service
public class FeedbackWebhookService {
    private final FeedbackRequestRepository requestRepository;
    private final FeedbackWebhookEventRepository eventRepository;
    private final Clock clock;

    public FeedbackWebhookService(FeedbackRequestRepository requestRepository,
                                  FeedbackWebhookEventRepository eventRepository, Clock clock) {
        this.requestRepository = requestRepository;
        this.eventRepository = eventRepository;
        this.clock = clock;
    }

    @Transactional
    public boolean accept(DeliveryCallbackRequest callback) {
        Instant now = clock.instant();
        if (callback.eventTimestamp().isAfter(now.plusSeconds(300))
                || callback.eventTimestamp().isBefore(now.minus(Duration.ofDays(7)))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Delivery event timestamp is outside the accepted window.");
        }
        FeedbackRequest request = requestRepository.lockById(callback.feedbackRequestId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Feedback request was not found."));
        if (eventRepository.existsByEventId(callback.eventId())) {
            return false;
        }
        if (!callback.deliveryEventId().equals(request.getDeliveryEventId())) {
            saveEvent(callback, request);
            return false;
        }
        if (callback.providerMessageId() != null && request.getProviderMessageId() != null
                && !request.getProviderMessageId().equals(callback.providerMessageId())) {
            throw new ApiException(HttpStatus.CONFLICT, "Delivery callback does not match the recorded provider message.");
        }

        saveEvent(callback, request);
        if (request.getStatus() == FeedbackRequestStatus.COMPLETED
                || request.getStatus() == FeedbackRequestStatus.CANCELLED
                || request.getStatus() == FeedbackRequestStatus.EXPIRED) {
            return true;
        }
        if (callback.providerMessageId() != null && request.getProviderMessageId() == null) {
            request.setProviderMessageId(callback.providerMessageId());
        }
        switch (callback.status()) {
            case SENT -> {
                if ((request.getStatus() == FeedbackRequestStatus.PENDING
                        || request.getStatus() == FeedbackRequestStatus.SCHEDULED)
                        && notOlderThanSent(request, callback.eventTimestamp())) {
                    request.setStatus(FeedbackRequestStatus.SENT);
                    request.setSentAt(callback.eventTimestamp());
                }
            }
            case DELIVERED -> {
                if (notOlderThanSent(request, callback.eventTimestamp())
                        && (request.getStatus() != FeedbackRequestStatus.DELIVERED
                        || request.getDeliveredAt() == null
                        || callback.eventTimestamp().isAfter(request.getDeliveredAt()))) {
                    request.setStatus(FeedbackRequestStatus.DELIVERED);
                    if (request.getSentAt() == null) {
                        request.setSentAt(callback.eventTimestamp());
                    }
                    request.setDeliveredAt(callback.eventTimestamp());
                    request.setFailureReason(null);
                }
            }
            case FAILED -> {
                if (request.getStatus() != FeedbackRequestStatus.DELIVERED
                        && request.getStatus() != FeedbackRequestStatus.FAILED
                        && notOlderThanSent(request, callback.eventTimestamp())) {
                    request.setStatus(FeedbackRequestStatus.FAILED);
                    request.setFailureReason("WhatsApp delivery failed.");
                }
            }
        }
        return true;
    }

    private void saveEvent(DeliveryCallbackRequest callback, FeedbackRequest request) {
        FeedbackWebhookEvent event = new FeedbackWebhookEvent();
        event.setEventId(callback.eventId());
        event.setFeedbackRequest(request);
        event.setProviderMessageId(callback.providerMessageId());
        eventRepository.saveAndFlush(event);
    }

    private boolean notOlderThanSent(FeedbackRequest request, Instant eventTimestamp) {
        return request.getSentAt() == null || !eventTimestamp.isBefore(request.getSentAt());
    }
}
