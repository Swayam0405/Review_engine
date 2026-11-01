package review_engine.project.service;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Coordinates summary generation across application instances using a bounded database lease.
 */
@Service
public class InsightSummaryGenerationLockService {
    private static final Duration LEASE = Duration.ofMinutes(20);
    private final JdbcTemplate jdbc;

    public InsightSummaryGenerationLockService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UUID> acquire(UUID businessId, Instant from, Instant to, Instant now) {
        UUID token = UUID.randomUUID();
        try {
            UUID acquired = jdbc.queryForObject("""
                    insert into insight_summary_generation_locks
                        (business_id, period_start, period_end, lock_token, locked_until)
                    values (?, ?, ?, ?, ?)
                    on conflict (business_id, period_start, period_end)
                    do update set lock_token = excluded.lock_token, locked_until = excluded.locked_until
                    where insight_summary_generation_locks.locked_until <= ?
                    returning lock_token
                    """, UUID.class, businessId, Timestamp.from(from), Timestamp.from(to), token,
                    Timestamp.from(now.plus(LEASE)), Timestamp.from(now));
            return Optional.ofNullable(acquired);
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public void release(UUID token) {
        jdbc.update("delete from insight_summary_generation_locks where lock_token = ?", token);
    }
}
