package review_engine.project.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import review_engine.project.entity.Feedback;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Collection;
import java.util.UUID;

/**
 * Final feedback persistence with database unique constraints guarding duplicate submissions.
 */
public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {
    boolean existsByFeedbackRequestId(UUID feedbackRequestId);

    Optional<Feedback> findByFeedbackRequestIdAndBusinessId(UUID feedbackRequestId, UUID businessId);

    List<Feedback> findAllByFeedbackRequestIdInAndBusinessId(
            Collection<UUID> feedbackRequestIds, UUID businessId);

    Optional<Feedback> findByIdAndBusinessId(UUID id, UUID businessId);

    boolean existsByIdAndBusinessId(UUID id, UUID businessId);

    List<Feedback> findAllByBusinessIdAndSubmittedAtGreaterThanEqualAndSubmittedAtLessThan(
            UUID businessId, Instant from, Instant to, Pageable pageable);

    long countByBusinessIdAndSubmittedAtGreaterThanEqualAndSubmittedAtLessThan(
            UUID businessId, Instant from, Instant to);

    Optional<Feedback> findFirstByBusinessIdAndSubmittedAtGreaterThanEqualAndSubmittedAtLessThanOrderBySubmittedAtDesc(
            UUID businessId, Instant from, Instant to);
}
