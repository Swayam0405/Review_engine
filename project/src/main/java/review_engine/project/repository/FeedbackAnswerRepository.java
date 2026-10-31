package review_engine.project.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import review_engine.project.entity.FeedbackAnswer;

import java.util.UUID;

/**
 * Structured answer persistence.
 */
public interface FeedbackAnswerRepository extends JpaRepository<FeedbackAnswer, UUID> {
    boolean existsByQuestionId(UUID questionId);

    java.util.List<FeedbackAnswer> findAllByFeedbackId(UUID feedbackId);
}
