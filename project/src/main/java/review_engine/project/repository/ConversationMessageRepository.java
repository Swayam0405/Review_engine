package review_engine.project.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import review_engine.project.entity.ConversationMessage;

import java.util.List;
import java.util.UUID;

/**
 * Append-only messages ordered chronologically for conversation reads.
 */
public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, UUID> {
    List<ConversationMessage> findAllByConversationIdOrderByCreatedAtAscIdAsc(UUID conversationId);
}
