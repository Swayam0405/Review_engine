package review_engine.project.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import review_engine.project.ai.dto.AnalysisSentiment;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable AI interpretation kept separate from the customer's submitted feedback.
 */
@Entity
@Table(name = "feedback_analysis", uniqueConstraints =
        @UniqueConstraint(name = "uk_feedback_analysis_feedback", columnNames = "feedback_id"))
@Getter
@Setter
@NoArgsConstructor
public class FeedbackAnalysis {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feedback_id", nullable = false)
    private Feedback feedback;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AnalysisSentiment sentiment;

    @Column(name = "sentiment_confidence", nullable = false)
    private double sentimentConfidence;

    @Column(name = "sentiment_summary", nullable = false, columnDefinition = "text")
    private String sentimentSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String topics;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String aspects;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String issues;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "positive_highlights", nullable = false, columnDefinition = "jsonb")
    private String positiveHighlights;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "negative_highlights", nullable = false, columnDefinition = "jsonb")
    private String negativeHighlights;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "improvement_opportunities", nullable = false, columnDefinition = "jsonb")
    private String improvementOpportunities;

    @Column(name = "ai_provider", nullable = false, length = 64)
    private String aiProvider;

    @Column(name = "ai_model", nullable = false, length = 128)
    private String aiModel;

    @Column(name = "analyzed_at", nullable = false)
    private Instant analyzedAt;

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
