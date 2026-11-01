package review_engine.project.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import review_engine.project.entity.ReviewResponseDraft;

import java.util.Optional;
import java.util.UUID;

/**
 * Review reply drafts with business-scoped listings and locked state transitions.
 */
public interface ReviewResponseDraftRepository extends JpaRepository<ReviewResponseDraft, UUID> {
    Page<ReviewResponseDraft> findAllByGoogleReviewIdAndBusinessId(
            UUID googleReviewId, UUID businessId, Pageable pageable);

    Optional<ReviewResponseDraft> findByIdAndBusinessId(UUID id, UUID businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from ReviewResponseDraft d where d.id = :id and d.business.id = :businessId")
    Optional<ReviewResponseDraft> lockByIdAndBusinessId(@Param("id") UUID id, @Param("businessId") UUID businessId);
}
