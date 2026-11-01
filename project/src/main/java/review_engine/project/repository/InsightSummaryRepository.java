package review_engine.project.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import review_engine.project.entity.InsightSummary;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Cached summaries keyed by exact business/date periods.
 */
public interface InsightSummaryRepository extends JpaRepository<InsightSummary, UUID> {
    Optional<InsightSummary> findByBusinessIdAndPeriodStartAndPeriodEnd(
            UUID businessId, Instant periodStart, Instant periodEnd);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InsightSummary s where s.business.id = :businessId "
            + "and s.periodStart = :periodStart and s.periodEnd = :periodEnd")
    Optional<InsightSummary> findForUpdate(@Param("businessId") UUID businessId,
                                           @Param("periodStart") Instant periodStart,
                                           @Param("periodEnd") Instant periodEnd);
}
