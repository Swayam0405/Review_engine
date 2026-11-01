package review_engine.project.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import review_engine.project.entity.GoogleReview;

import java.util.Optional;
import java.util.UUID;

/**
 * Imported Google reviews, always resolved inside the owner's business.
 */
public interface GoogleReviewRepository extends JpaRepository<GoogleReview, UUID> {
    Optional<GoogleReview> findByIdAndBusinessId(UUID id, UUID businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from GoogleReview r where r.id = :id and r.business.id = :businessId")
    Optional<GoogleReview> lockByIdAndBusinessId(@Param("id") UUID id, @Param("businessId") UUID businessId);

    boolean existsByBusinessIdAndSourceReviewId(UUID businessId, String sourceReviewId);

    Optional<GoogleReview> findByBusinessIdAndSourceReviewId(UUID businessId, String sourceReviewId);

    boolean existsByIdAndBusinessId(UUID id, UUID businessId);
}
