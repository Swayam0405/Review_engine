package review_engine.project.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import review_engine.project.entity.FeedbackAnalysis;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persisted analyses scoped to a business and time window.
 */
public interface FeedbackAnalysisRepository extends JpaRepository<FeedbackAnalysis, UUID> {
    Optional<FeedbackAnalysis> findByFeedbackIdAndBusinessId(UUID feedbackId, UUID businessId);

    boolean existsByFeedbackId(UUID feedbackId);

    List<FeedbackAnalysis> findAllByBusinessIdAndAnalyzedAtGreaterThanEqualAndAnalyzedAtLessThan(
            UUID businessId, Instant from, Instant to, Pageable pageable);

    long countByBusinessIdAndAnalyzedAtGreaterThanEqualAndAnalyzedAtLessThan(
            UUID businessId, Instant from, Instant to);
}
