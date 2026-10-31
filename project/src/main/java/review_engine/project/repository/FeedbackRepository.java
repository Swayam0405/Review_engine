package review_engine.project.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import review_engine.project.entity.Feedback;

import java.util.UUID;

/**
 * Final feedback persistence with database unique constraints guarding duplicate submissions.
 */
public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {
    boolean existsByFeedbackRequestId(UUID feedbackRequestId);
}
