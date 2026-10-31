package review_engine.project.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import review_engine.project.enums.FeedbackRequestStatus;
import review_engine.project.repository.FeedbackRequestRepository;

import java.time.Clock;

/**
 * Persists expiration state for expired, incomplete feedback invitations.
 */
@Service
public class FeedbackExpiryService {
    private final FeedbackRequestRepository requestRepository;
    private final Clock clock;

    public FeedbackExpiryService(FeedbackRequestRepository requestRepository, Clock clock) {
        this.requestRepository = requestRepository;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${review-engine.feedback.expiry-poll-interval-ms:60000}")
    @Transactional
    public void expireRequests() {
        requestRepository.findExpired(clock.instant(), PageRequest.of(0, 100))
                .forEach(request -> request.setStatus(FeedbackRequestStatus.EXPIRED));
    }
}
