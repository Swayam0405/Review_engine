package review_engine.project.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import review_engine.project.entity.FeedbackOutboxEvent;
import review_engine.project.enums.FeedbackOutboxStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable outbox access with row locking for multi-instance workers.
 */
public interface FeedbackOutboxRepository extends JpaRepository<FeedbackOutboxEvent, UUID> {
    java.util.Optional<FeedbackOutboxEvent> findByIdAndAggregateId(UUID id, UUID aggregateId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e from FeedbackOutboxEvent e
            where e.status in (review_engine.project.enums.FeedbackOutboxStatus.PENDING,
                               review_engine.project.enums.FeedbackOutboxStatus.PROCESSING)
              and e.nextAttemptAt <= :now
            order by e.createdAt asc
            """)
    List<FeedbackOutboxEvent> findReadyForUpdate(
            @Param("now") Instant now,
            org.springframework.data.domain.Pageable pageable);
}
