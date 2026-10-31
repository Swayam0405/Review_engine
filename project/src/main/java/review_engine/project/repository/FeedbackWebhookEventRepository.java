package review_engine.project.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import review_engine.project.entity.FeedbackWebhookEvent;

/**
 * Persistent callback idempotency keys.
 */
public interface FeedbackWebhookEventRepository extends JpaRepository<FeedbackWebhookEvent, String> {
    boolean existsByEventId(String eventId);
}
