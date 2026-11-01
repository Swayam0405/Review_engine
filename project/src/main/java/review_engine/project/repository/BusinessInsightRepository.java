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
                       count(a.id) filter (where a.sentiment = 'MIXED') as sentiment_mixed,
                       count(a.id) as analyzed,
                       count(f.id) filter (where a.id is null and o.status = 'FAILED') as analysis_failed,
                       count(f.id) filter (where a.id is null
                           and coalesce(o.status, 'PENDING') in ('PENDING', 'PROCESSING'))
                           as analysis_pending
                from feedback f
                left join feedback_analysis a on a.feedback_id = f.id and a.business_id = f.business_id
                left join feedback_outbox o on o.aggregate_id = f.id and o.event_type = 'FEEDBACK_ANALYSIS'
                where f.business_id = ? and f.submitted_at >= ? and f.submitted_at < ?
                """, (rs, rowNum) -> new OverviewRow(
                rs.getLong("total"), rs.getDouble("average_rating"),
                rs.getLong("rating_1"), rs.getLong("rating_2"), rs.getLong("rating_3"),
                rs.getLong("rating_4"), rs.getLong("rating_5"),
                rs.getLong("sentiment_positive"), rs.getLong("sentiment_neutral"),
                rs.getLong("sentiment_negative"), rs.getLong("sentiment_mixed"),
                rs.getLong("analyzed"), rs.getLong("analysis_pending"), rs.getLong("analysis_failed")),
                businessId, Timestamp.from(from), Timestamp.from(to));
    }

    public List<TopicRow> topics(UUID businessId, Instant from, Instant to) {
        return topics(businessId, from, to, null, 100);
    }

    public List<TopicRow> topics(UUID businessId, Instant from, Instant to, String sentiment, int limit) {
        return jdbc.query("""
                select lower(btrim(topic->>'name')) as name,
                       count(distinct a.feedback_id) as mentions,
                       count(distinct a.feedback_id) filter (where topic->>'sentiment' = 'POSITIVE') as positive,
                       count(distinct a.feedback_id) filter (where topic->>'sentiment' = 'NEUTRAL') as neutral,
                       count(distinct a.feedback_id) filter (where topic->>'sentiment' = 'NEGATIVE') as negative,
                       count(distinct a.feedback_id) filter (where topic->>'sentiment' = 'MIXED') as mixed
                from feedback_analysis a
                join feedback f on f.id = a.feedback_id and f.business_id = a.business_id
                cross join lateral jsonb_array_elements(a.topics) as topic
                where a.business_id = ? and f.submitted_at >= ? and f.submitted_at < ?
                group by lower(btrim(topic->>'name'))
                having cast(? as varchar) is null
                    or count(distinct a.feedback_id) filter (where topic->>'sentiment' = cast(? as varchar)) > 0
                order by mentions desc, name asc
                limit ?
                """, (rs, rowNum) -> new TopicRow(rs.getString("name"), rs.getLong("mentions"),
                rs.getLong("positive"), rs.getLong("neutral"), rs.getLong("negative"), rs.getLong("mixed")),
                businessId, Timestamp.from(from), Timestamp.from(to), sentiment, sentiment, limit);
    }

    public List<IssueRow> issues(UUID businessId, Instant from, Instant to) {
        return issues(businessId, from, to, 1, null, 100);
    }

    public List<IssueRow> issues(UUID businessId, Instant from, Instant to, int minimumReports,
                                 String severity, int limit) {
        return jdbc.query("""
                select lower(btrim(issue->>'name')) as name,
                       min(issue->>'description') as description,
                       count(distinct a.feedback_id) as frequency,
                       count(distinct a.feedback_id) filter (where issue->>'severity' = 'LOW') as low_count,
                       count(distinct a.feedback_id) filter (where issue->>'severity' = 'MEDIUM') as medium_count,
                       count(distinct a.feedback_id) filter (where issue->>'severity' = 'HIGH') as high_count,
                       string_agg(distinct nullif(issue->>'topic', ''), ', ') as related_topics,
                       min(f.submitted_at) as first_reported_at,
                       max(f.submitted_at) as last_reported_at,
                       count(distinct f.id) filter (where a.sentiment = 'POSITIVE') as positive,
                       count(distinct f.id) filter (where a.sentiment = 'NEUTRAL') as neutral,
                       count(distinct f.id) filter (where a.sentiment = 'NEGATIVE') as negative,
                       count(distinct f.id) filter (where a.sentiment = 'MIXED') as mixed
                from feedback_analysis a
                join feedback f on f.id = a.feedback_id and f.business_id = a.business_id
                cross join lateral jsonb_array_elements(a.issues) as issue
                where a.business_id = ? and f.submitted_at >= ? and f.submitted_at < ?
                group by lower(btrim(issue->>'name'))
                having count(distinct a.feedback_id) >= ?
                   and (cast(? as varchar) is null
                        or case cast(? as varchar)
                            when 'LOW' then count(distinct a.feedback_id)
                                filter (where issue->>'severity' = 'LOW') > 0
                            when 'MEDIUM' then count(distinct a.feedback_id)
                                filter (where issue->>'severity' = 'MEDIUM') > 0
                            when 'HIGH' then count(distinct a.feedback_id)
                                filter (where issue->>'severity' = 'HIGH') > 0
                            else false
                           end)
                order by frequency desc, name asc
                limit ?
                """, (rs, rowNum) -> new IssueRow(rs.getString("name"), rs.getString("description"),
                rs.getLong("frequency"), rs.getLong("low_count"), rs.getLong("medium_count"),
                rs.getLong("high_count"), rs.getString("related_topics"),
                rs.getTimestamp("first_reported_at").toInstant(), rs.getTimestamp("last_reported_at").toInstant(),
                rs.getLong("positive"), rs.getLong("neutral"), rs.getLong("negative"), rs.getLong("mixed")),
                businessId, Timestamp.from(from), Timestamp.from(to), minimumReports, severity, severity, limit);
    }

    public List<TrendRow> trends(UUID businessId, Instant from, Instant to) {
        return trends(businessId, from, to, "day");
    }

    public List<TrendRow> trends(UUID businessId, Instant from, Instant to, String interval) {
        return jdbc.query("""
                select date_trunc(?, f.submitted_at at time zone 'UTC') as period_start,
                       count(*) as feedback_count,
                       avg(f.rating) as average_rating,
                       count(a.id) filter (where a.sentiment = 'POSITIVE') as positive,
                       count(a.id) filter (where a.sentiment = 'NEUTRAL') as neutral,
                       count(a.id) filter (where a.sentiment = 'NEGATIVE') as negative,
                       count(a.id) filter (where a.sentiment = 'MIXED') as mixed,
                       count(a.id) as analyzed,
                       count(f.id) filter (where a.id is null and o.status = 'FAILED') as analysis_failed,
                       count(f.id) filter (where a.id is null
                           and coalesce(o.status, 'PENDING') in ('PENDING', 'PROCESSING'))
                           as analysis_pending
                from feedback f
                left join feedback_analysis a on a.feedback_id = f.id and a.business_id = f.business_id
                left join feedback_outbox o on o.aggregate_id = f.id and o.event_type = 'FEEDBACK_ANALYSIS'
                where f.business_id = ? and f.submitted_at >= ? and f.submitted_at < ?
                group by period_start
                order by period_start asc
                """, (rs, rowNum) -> new TrendRow(
                rs.getTimestamp("period_start").toLocalDateTime().toInstant(ZoneOffset.UTC),
                rs.getLong("feedback_count"), rs.getDouble("average_rating"),
                rs.getLong("positive"), rs.getLong("neutral"), rs.getLong("negative"), rs.getLong("mixed"),
                rs.getLong("analyzed"), rs.getLong("analysis_pending"), rs.getLong("analysis_failed")),
                interval, businessId, Timestamp.from(from), Timestamp.from(to));
    }

    public List<AspectRow> aspects(UUID businessId, Instant from, Instant to, int limit) {
        return jdbc.query("""
                with aspect_rows as (
                    select distinct a.feedback_id, a.business_id,
                           lower(btrim(aspect->>'name')) as aspect_name,
                           aspect->>'sentiment' as sentiment,
                           a.sentiment_confidence
                    from feedback_analysis a
                    join feedback f on f.id = a.feedback_id and f.business_id = a.business_id
                    cross join lateral jsonb_array_elements(a.aspects) as aspect
                    where a.business_id = ? and f.submitted_at >= ? and f.submitted_at < ?
                ), sentiment_counts as (
                    select aspect_name,
                           count(distinct feedback_id) as mentions,
                           count(distinct feedback_id) filter (where sentiment = 'POSITIVE') as positive,
                           count(distinct feedback_id) filter (where sentiment = 'NEUTRAL') as neutral,
                           count(distinct feedback_id) filter (where sentiment = 'NEGATIVE') as negative,
                           count(distinct feedback_id) filter (where sentiment = 'MIXED') as mixed
                    from aspect_rows
                    group by aspect_name
                ), confidence_means as (
                    select aspect_name, avg(sentiment_confidence) as average_confidence
                    from (
                        select distinct feedback_id, aspect_name, sentiment_confidence
                        from aspect_rows
                    ) per_feedback
                    group by aspect_name
                )
                select counts.aspect_name as name, counts.mentions, counts.positive, counts.neutral,
                       counts.negative, counts.mixed, means.average_confidence
                from sentiment_counts counts
                join confidence_means means using (aspect_name)
                order by counts.mentions desc, name asc
                limit ?
                """, (rs, rowNum) -> new AspectRow(rs.getString("name"), rs.getLong("mentions"),
                rs.getLong("positive"), rs.getLong("neutral"), rs.getLong("negative"), rs.getLong("mixed"),
                rs.getDouble("average_confidence")),
                businessId, Timestamp.from(from), Timestamp.from(to), limit);
    }

    public List<HighlightRow> highlights(UUID businessId, Instant from, Instant to, int limit) {
        return jdbc.query("""
                with grouped as (
                    select aspect->>'sentiment' as sentiment,
                           lower(btrim(aspect->>'name')) as aspect_name,
                           min(aspect->>'explanation') as explanation,
                           count(distinct f.id) as supporting_feedback
                    from feedback_analysis a
                    join feedback f on f.id = a.feedback_id and f.business_id = a.business_id
                    cross join lateral jsonb_array_elements(a.aspects) as aspect
                    where a.business_id = ? and f.submitted_at >= ? and f.submitted_at < ?
                      and aspect->>'sentiment' in ('POSITIVE', 'NEGATIVE')
                    group by aspect->>'sentiment', lower(btrim(aspect->>'name'))
                ), ranked as (
                    select grouped.*,
                           row_number() over (
                               partition by sentiment order by supporting_feedback desc, aspect_name
                           ) as rank
                    from grouped
                )
                select sentiment, aspect_name, explanation, supporting_feedback
                from ranked
                where rank <= ?
                order by sentiment, rank
                """, (rs, rowNum) -> new HighlightRow(rs.getString("sentiment"), rs.getString("aspect_name"),
                rs.getString("explanation"), rs.getLong("supporting_feedback")),
                businessId, Timestamp.from(from), Timestamp.from(to), limit);
    }

    public ActivityRow activity(UUID businessId, Instant from, Instant to) {
        return jdbc.queryForObject("""
                select
                  (select count(*) from feedback_requests r
                   where r.business_id = ? and r.created_at >= ? and r.created_at < ?) as total_requests,
                  (select count(*) from feedback_requests r
                   where r.business_id = ? and r.created_at >= ? and r.created_at < ? and r.status = 'PENDING') as pending_requests,
                  (select count(*) from feedback_requests r
                   where r.business_id = ? and r.created_at >= ? and r.created_at < ? and r.status = 'SCHEDULED') as scheduled_requests,
                  (select count(*) from feedback_requests r
                   where r.business_id = ? and r.created_at >= ? and r.created_at < ? and r.status = 'SENT') as sent_requests,
                  (select count(*) from feedback_requests r
                   where r.business_id = ? and r.created_at >= ? and r.created_at < ? and r.status = 'DELIVERED') as delivered_requests,
                  (select count(*) from feedback_requests r
                   where r.business_id = ? and r.created_at >= ? and r.created_at < ? and r.status = 'FAILED') as failed_requests,
                  (select count(*) from feedback_requests r
                   where r.business_id = ? and r.created_at >= ? and r.created_at < ? and r.status = 'COMPLETED') as completed_requests,
                  (select count(*) from feedback_requests r
                   where r.business_id = ? and r.created_at >= ? and r.created_at < ? and r.status = 'EXPIRED') as expired_requests,
                  (select count(*) from feedback_conversations c
                   where c.business_id = ? and c.created_at >= ? and c.created_at < ? and c.status = 'IN_PROGRESS') as conversations_in_progress,
                  (select count(*) from feedback_conversations c
                   where c.business_id = ? and c.created_at >= ? and c.created_at < ? and c.status = 'COMPLETED') as conversations_completed,
                  (select count(*) from feedback f
                   join feedback_outbox o on o.aggregate_id = f.id and o.event_type = 'FEEDBACK_ANALYSIS'
                   left join feedback_analysis a on a.feedback_id = f.id and a.business_id = f.business_id
                   where f.business_id = ? and f.submitted_at >= ? and f.submitted_at < ?
                     and a.id is null and o.status in ('PENDING', 'PROCESSING')) as analysis_pending,
                  (select count(*) from feedback f
                   join feedback_outbox o on o.aggregate_id = f.id and o.event_type = 'FEEDBACK_ANALYSIS'
                   left join feedback_analysis a on a.feedback_id = f.id and a.business_id = f.business_id
                   where f.business_id = ? and f.submitted_at >= ? and f.submitted_at < ?
                     and a.id is null and o.status = 'FAILED') as analysis_failed
                """, (rs, rowNum) -> new ActivityRow(
                rs.getLong("total_requests"), rs.getLong("pending_requests"), rs.getLong("scheduled_requests"),
                rs.getLong("sent_requests"), rs.getLong("delivered_requests"), rs.getLong("failed_requests"),
                rs.getLong("completed_requests"), rs.getLong("expired_requests"),
                rs.getLong("conversations_in_progress"), rs.getLong("conversations_completed"),
                rs.getLong("analysis_pending"), rs.getLong("analysis_failed")),
                repeatedParams(businessId, from, to, 12));
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

    public SummaryFreshness summaryFreshness(UUID businessId, Instant from, Instant to) {
        return jdbc.queryForObject("""
                select count(f.id) as feedback_count,
                       count(a.id) as analyzed_count,
                       max(f.submitted_at) as latest_feedback_at
                from feedback f
                left join feedback_analysis a on a.feedback_id = f.id and a.business_id = f.business_id
                where f.business_id = ? and f.submitted_at >= ? and f.submitted_at < ?
                """, (rs, rowNum) -> new SummaryFreshness(rs.getInt("feedback_count"),
                rs.getInt("analyzed_count"), rs.getTimestamp("latest_feedback_at") == null
                ? null : rs.getTimestamp("latest_feedback_at").toInstant()),
                businessId, Timestamp.from(from), Timestamp.from(to));
    }

    public record OverviewRow(long total, double averageRating, long rating1, long rating2, long rating3,
                              long rating4, long rating5, long positive, long neutral, long negative, long mixed,
                              long analyzed, long analysisPending, long analysisFailed) {
    }

    public record TopicRow(String name, long mentions, long positive, long neutral, long negative, long mixed) {
    }

    public record IssueRow(String name, String description, long frequency, long low, long medium, long high,
                           String relatedTopics, Instant firstReportedAt, Instant lastReportedAt,
                           long positive, long neutral, long negative, long mixed) {
    }

    public record TrendRow(Instant day, long feedbackCount, double averageRating,
                           long positive, long neutral, long negative, long mixed, long analyzed,
                           long analysisPending, long analysisFailed) {
    }

    public record FeedbackSample(int rating, String comment, String analysisSummary, String sentiment) {
    }

    public record AspectRow(String name, long mentions, long positive, long neutral, long negative,
                            long mixed, double averageConfidence) {
    }

    public record HighlightRow(String sentiment, String aspect, String explanation, long supportingFeedback) {
    }

    public record ActivityRow(long totalRequests, long pendingRequests, long scheduledRequests, long sentRequests,
                              long deliveredRequests, long failedRequests, long completedRequests, long expiredRequests,
                              long conversationsInProgress, long conversationsCompleted, long analysisPending,
                              long analysisFailed) {
    }

    public record SummaryFreshness(int feedbackCount, int analyzedFeedbackCount, Instant latestFeedbackAt) {
    }

    private Object[] repeatedParams(UUID businessId, Instant from, Instant to, int repetitions) {
        Object[] values = new Object[repetitions * 3];
        for (int i = 0; i < repetitions; i++) {
            values[i * 3] = businessId;
            values[i * 3 + 1] = Timestamp.from(from);
            values[i * 3 + 2] = Timestamp.from(to);
        }
        return values;
    }
}
