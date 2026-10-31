package review_engine.project.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import review_engine.project.entity.FeedbackOutboxEvent;
import review_engine.project.entity.FeedbackRequest;
import review_engine.project.enums.FeedbackOutboxStatus;
import review_engine.project.enums.FeedbackRequestStatus;
import review_engine.project.exception.ApiException;
import review_engine.project.repository.FeedbackOutboxRepository;
import review_engine.project.repository.FeedbackRequestRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Claims durable outbox records in short transactions and records explicit delivery outcomes.
 */
@Service
public class FeedbackOutboxService {
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration CLAIM_LEASE = Duration.ofMinutes(5);
    private final FeedbackOutboxRepository outboxRepository;
    private final FeedbackRequestRepository requestRepository;
    private final Clock clock;

    public FeedbackOutboxService(FeedbackOutboxRepository outboxRepository,
                                 FeedbackRequestRepository requestRepository, Clock clock) {
        this.outboxRepository = outboxRepository;
        this.requestRepository = requestRepository;
        this.clock = clock;
    }

    @Transactional
    public Optional<OutboxEvent> claimNext() {
        Instant now = clock.instant();
        for (FeedbackOutboxEvent event : outboxRepository.findReadyForUpdate(now, PageRequest.of(0, 25))) {
            if (event.getStatus() == FeedbackOutboxStatus.PROCESSING
                    && event.getAttemptCount() >= MAX_ATTEMPTS) {
                event.setStatus(FeedbackOutboxStatus.FAILED);
                event.setLastError("Delivery attempt limit reached.");
                markRequestFailed(event.getAggregateId(), event.getLastError());
                continue;
            }
            event.setStatus(FeedbackOutboxStatus.PROCESSING);
            event.setAttemptCount(event.getAttemptCount() + 1);
            event.setNextAttemptAt(now.plus(CLAIM_LEASE));
            return Optional.of(new OutboxEvent(event.getId(), event.getEventType(),
                    event.getAggregateId(), event.getPayload(), event.getAttemptCount()));
        }
        return Optional.empty();
    }

    @Transactional
    public void markProcessed(UUID eventId) {
        FeedbackOutboxEvent event = outboxRepository.findById(eventId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Outbox event was not found."));
        event.setStatus(FeedbackOutboxStatus.PROCESSED);
        event.setProcessedAt(clock.instant());
        event.setLastError(null);
        FeedbackRequest request = requestRepository.lockById(event.getAggregateId()).orElse(null);
        if (request != null && request.getStatus() == FeedbackRequestStatus.PENDING) {
            request.setStatus(FeedbackRequestStatus.SCHEDULED);
        }
    }

    @Transactional
    public void markFailed(UUID eventId, String safeError) {
        FeedbackOutboxEvent event = outboxRepository.findById(eventId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Outbox event was not found."));
        event.setLastError(safeError);
        if (event.getAttemptCount() >= MAX_ATTEMPTS) {
            event.setStatus(FeedbackOutboxStatus.FAILED);
            markRequestFailed(event.getAggregateId(), "Scheduling could not be completed.");
        } else {
            event.setStatus(FeedbackOutboxStatus.PENDING);
            long retrySeconds = Math.min(300, 1L << Math.min(event.getAttemptCount(), 8));
            event.setNextAttemptAt(clock.instant().plusSeconds(retrySeconds));
        }
    }

    private void markRequestFailed(UUID requestId, String reason) {
        requestRepository.lockById(requestId).ifPresent(request -> {
            if (request.getStatus() == FeedbackRequestStatus.PENDING
                    || request.getStatus() == FeedbackRequestStatus.SCHEDULED) {
                request.setStatus(FeedbackRequestStatus.FAILED);
                request.setFailureReason(reason);
            }
        });
    }

    public record OutboxEvent(UUID id, String eventType, UUID aggregateId, String payload, int attemptCount) {
    }
}
