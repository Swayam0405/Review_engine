package review_engine.project.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sends claimed outbox events outside database transactions.
 */
@Component
public class FeedbackOutboxDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(FeedbackOutboxDispatcher.class);
    private final FeedbackOutboxService outboxService;
    private final N8nIntegrationService n8n;

    public FeedbackOutboxDispatcher(FeedbackOutboxService outboxService, N8nIntegrationService n8n) {
        this.outboxService = outboxService;
        this.n8n = n8n;
    }

    @Scheduled(fixedDelayString = "${review-engine.n8n.poll-interval-ms:5000}")
    public void dispatch() {
        if (!n8n.isEnabled()) {
            return;
        }
        for (int i = 0; i < 10; i++) {
            var claimed = outboxService.claimNext();
            if (claimed.isEmpty()) {
                return;
            }
            FeedbackOutboxService.OutboxEvent event = claimed.get();
            try {
                n8n.schedule(event.id(), event.payload());
                outboxService.markProcessed(event.id());
            } catch (N8nIntegrationService.N8nDeliveryException exception) {
                LOGGER.warn("Feedback schedule event {} failed on attempt {}.", event.id(), event.attemptCount());
                outboxService.markFailed(event.id(), "n8n scheduling request failed.");
            }
        }
    }
}
