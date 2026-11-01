package review_engine.project.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import review_engine.project.entity.FeedbackRequest;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Feedback invitation persistence, including locked token and lifecycle access.
 */
public interface FeedbackRequestRepository extends JpaRepository<FeedbackRequest, UUID> {
    Optional<FeedbackRequest> findByIdAndBusinessId(UUID id, UUID businessId);

    Optional<FeedbackRequest> findByTransactionId(UUID transactionId);

    Optional<FeedbackRequest> findByRequestTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from FeedbackRequest r where r.requestTokenHash = :tokenHash")
    Optional<FeedbackRequest> findByRequestTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    Page<FeedbackRequest> findAllByBusinessId(UUID businessId, Pageable pageable);

    Page<FeedbackRequest> findAllByBusinessIdAndStatus(
            UUID businessId, review_engine.project.enums.FeedbackRequestStatus status, Pageable pageable);

    boolean existsByIdAndBusinessId(UUID id, UUID businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from FeedbackRequest r where r.id = :id and r.business.id = :businessId")
    Optional<FeedbackRequest> lockByIdAndBusinessId(@Param("id") UUID id, @Param("businessId") UUID businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from FeedbackRequest r where r.id = :id")
    Optional<FeedbackRequest> lockById(@Param("id") UUID id);

    @Query("""
            select r from FeedbackRequest r
            where r.status in (review_engine.project.enums.FeedbackRequestStatus.PENDING,
                               review_engine.project.enums.FeedbackRequestStatus.SCHEDULED,
                               review_engine.project.enums.FeedbackRequestStatus.SENT,
                               review_engine.project.enums.FeedbackRequestStatus.DELIVERED)
              and r.expiresAt <= :now
            """)
    java.util.List<FeedbackRequest> findExpired(
            @Param("now") Instant now, org.springframework.data.domain.Pageable pageable);
}
