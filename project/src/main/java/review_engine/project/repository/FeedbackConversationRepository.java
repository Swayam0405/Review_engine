package review_engine.project.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import review_engine.project.entity.FeedbackConversation;

import java.util.Optional;
import java.util.UUID;

/**
 * Conversation lookup by the owning feedback request.
 */
public interface FeedbackConversationRepository extends JpaRepository<FeedbackConversation, UUID> {
    Optional<FeedbackConversation> findByFeedbackRequestId(UUID feedbackRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from FeedbackConversation c where c.feedbackRequest.id = :requestId")
    Optional<FeedbackConversation> findByFeedbackRequestIdForUpdate(@Param("requestId") UUID requestId);
}
