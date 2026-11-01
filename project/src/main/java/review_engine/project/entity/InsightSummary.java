package review_engine.project.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Cached, evidence-grounded owner summary for a precise UTC date window.
 */
@Entity
@Table(name = "insight_summaries", uniqueConstraints =
        @UniqueConstraint(name = "uk_insight_summaries_business_period",
                columnNames = {"business_id", "period_start", "period_end"}))
@Getter
@Setter
@NoArgsConstructor
public class InsightSummary {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Column(name = "summary_text", nullable = false, columnDefinition = "text")
    private String summaryText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "positive_highlights", nullable = false, columnDefinition = "jsonb")
    private String positiveHighlights;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "negative_highlights", nullable = false, columnDefinition = "jsonb")
    private String negativeHighlights;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recurring_issues", nullable = false, columnDefinition = "jsonb")
    private String recurringIssues;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String recommendations;

    @Column(name = "feedback_count", nullable = false)
    private int feedbackCount;

    @Column(name = "analyzed_feedback_count", nullable = false)
    private int analyzedFeedbackCount;

    @Column(name = "latest_feedback_at")
    private Instant latestFeedbackAt;

    @Column(name = "ai_provider", nullable = false, length = 64)
    private String aiProvider;

    @Column(name = "ai_model", nullable = false, length = 128)
    private String aiModel;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
