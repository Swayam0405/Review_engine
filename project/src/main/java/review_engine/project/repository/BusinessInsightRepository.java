package review_engine.project.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Deterministic PostgreSQL aggregations; measurable dashboard metrics are never delegated to AI.
 */
@Repository
public class BusinessInsightRepository {
    private final JdbcTemplate jdbc;

    public BusinessInsightRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public OverviewRow overview(UUID businessId, Instant from, Instant to) {
        return jdbc.queryForObject("""
                select count(f.id) as total,
                       coalesce(avg(f.rating), 0) as average_rating,
                       count(f.id) filter (where f.rating = 1) as rating_1,
                       count(f.id) filter (where f.rating = 2) as rating_2,
                       count(f.id) filter (where f.rating = 3) as rating_3,
                       count(f.id) filter (where f.rating = 4) as rating_4,
                       count(f.id) filter (where f.rating = 5) as rating_5,
                       count(a.id) filter (where a.sentiment = 'POSITIVE') as sentiment_positive,
                       count(a.id) filter (where a.sentiment = 'NEUTRAL') as sentiment_neutral,
                       count(a.id) filter (where a.sentiment = 'NEGATIVE') as sentiment_negative,
                       count(a.id) filter (where a.sentiment = 'MIXED') as sentiment_mixed
                from feedback f
                left join feedback_analysis a on a.feedback_id = f.id and a.business_id = f.business_id
                where f.business_id = ? and f.submitted_at >= ? and f.submitted_at < ?
                """, (rs, rowNum) -> new OverviewRow(
                rs.getLong("total"), rs.getDouble("average_rating"),
                rs.getLong("rating_1"), rs.getLong("rating_2"), rs.getLong("rating_3"),
                rs.getLong("rating_4"), rs.getLong("rating_5"),
                rs.getLong("sentiment_positive"), rs.getLong("sentiment_neutral"),
                rs.getLong("sentiment_negative"), rs.getLong("sentiment_mixed")),
                businessId, Timestamp.from(from), Timestamp.from(to));
    }

    public List<TopicRow> topics(UUID businessId, Instant from, Instant to) {
        return jdbc.query("""
                select topic->>'name' as name,
                       count(distinct a.feedback_id) as mentions,
                       count(distinct a.feedback_id) filter (where topic->>'sentiment' = 'POSITIVE') as positive,
                       count(distinct a.feedback_id) filter (where topic->>'sentiment' = 'NEUTRAL') as neutral,
                       count(distinct a.feedback_id) filter (where topic->>'sentiment' = 'NEGATIVE') as negative,
                       count(distinct a.feedback_id) filter (where topic->>'sentiment' = 'MIXED') as mixed
                from feedback_analysis a
                join feedback f on f.id = a.feedback_id and f.business_id = a.business_id
                cross join lateral jsonb_array_elements(a.topics) as topic
                where a.business_id = ? and f.submitted_at >= ? and f.submitted_at < ?
                group by topic->>'name'
                order by mentions desc, name asc
                limit 100
                """, (rs, rowNum) -> new TopicRow(rs.getString("name"), rs.getLong("mentions"),
                rs.getLong("positive"), rs.getLong("neutral"), rs.getLong("negative"), rs.getLong("mixed")),
                businessId, Timestamp.from(from), Timestamp.from(to));
    }

    public List<IssueRow> issues(UUID businessId, Instant from, Instant to) {
        return jdbc.query("""
                select issue->>'name' as name,
                       min(issue->>'description') as description,
                       count(distinct a.feedback_id) as frequency,
                       count(distinct a.feedback_id) filter (where issue->>'severity' = 'LOW') as low_count,
                       count(distinct a.feedback_id) filter (where issue->>'severity' = 'MEDIUM') as medium_count,
                       count(distinct a.feedback_id) filter (where issue->>'severity' = 'HIGH') as high_count,
                       string_agg(distinct nullif(issue->>'topic', ''), ', ') as related_topics
                from feedback_analysis a
                join feedback f on f.id = a.feedback_id and f.business_id = a.business_id
                cross join lateral jsonb_array_elements(a.issues) as issue
                where a.business_id = ? and f.submitted_at >= ? and f.submitted_at < ?
                group by issue->>'name'
                order by frequency desc, name asc
                limit 100
                """, (rs, rowNum) -> new IssueRow(rs.getString("name"), rs.getString("description"),
                rs.getLong("frequency"), rs.getLong("low_count"), rs.getLong("medium_count"),
                rs.getLong("high_count"), rs.getString("related_topics")),
                businessId, Timestamp.from(from), Timestamp.from(to));
    }

    public List<TrendRow> trends(UUID businessId, Instant from, Instant to) {
        return jdbc.query("""
                select date_trunc('day', f.submitted_at at time zone 'UTC') as day,
                       count(*) as feedback_count,
                       avg(f.rating) as average_rating,
                       count(a.id) filter (where a.sentiment = 'POSITIVE') as positive,
                       count(a.id) filter (where a.sentiment = 'NEUTRAL') as neutral,
                       count(a.id) filter (where a.sentiment = 'NEGATIVE') as negative,
                       count(a.id) filter (where a.sentiment = 'MIXED') as mixed
                from feedback f
                left join feedback_analysis a on a.feedback_id = f.id and a.business_id = f.business_id
                where f.business_id = ? and f.submitted_at >= ? and f.submitted_at < ?
                group by day
                order by day asc
                """, (rs, rowNum) -> new TrendRow(
                rs.getTimestamp("day").toLocalDateTime().toInstant(ZoneOffset.UTC),
                rs.getLong("feedback_count"), rs.getDouble("average_rating"),
                rs.getLong("positive"), rs.getLong("neutral"), rs.getLong("negative"), rs.getLong("mixed")),
                businessId, Timestamp.from(from), Timestamp.from(to));
    }

    public List<FeedbackSample> summarySamples(UUID businessId, Instant from, Instant to, int limit) {
        return jdbc.query("""
                select f.rating, f.comment, a.sentiment_summary, a.sentiment
                from feedback f
                join feedback_analysis a on a.feedback_id = f.id and a.business_id = f.business_id
                where f.business_id = ? and f.submitted_at >= ? and f.submitted_at < ?
                order by f.submitted_at desc
                limit ?
                """, (rs, rowNum) -> new FeedbackSample(rs.getInt("rating"), rs.getString("comment"),
                rs.getString("sentiment_summary"), rs.getString("sentiment")),
                businessId, Timestamp.from(from), Timestamp.from(to), limit);
    }

    public record OverviewRow(long total, double averageRating, long rating1, long rating2, long rating3,
                              long rating4, long rating5, long positive, long neutral, long negative, long mixed) {
    }

    public record TopicRow(String name, long mentions, long positive, long neutral, long negative, long mixed) {
    }

    public record IssueRow(String name, String description, long frequency, long low, long medium, long high,
                           String relatedTopics) {
    }

    public record TrendRow(Instant day, long feedbackCount, double averageRating,
                           long positive, long neutral, long negative, long mixed) {
    }

    public record FeedbackSample(int rating, String comment, String analysisSummary, String sentiment) {
    }
}
