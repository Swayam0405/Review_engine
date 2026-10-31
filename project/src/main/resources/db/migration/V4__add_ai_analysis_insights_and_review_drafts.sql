ALTER TABLE conversation_messages ADD COLUMN idempotency_key VARCHAR(64);
CREATE UNIQUE INDEX uk_conversation_messages_idempotency
    ON conversation_messages(conversation_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE UNIQUE INDEX uk_feedback_outbox_analysis
    ON feedback_outbox(aggregate_id)
    WHERE event_type = 'FEEDBACK_ANALYSIS';

CREATE TABLE feedback_analysis (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE RESTRICT,
    feedback_id UUID NOT NULL UNIQUE,
    sentiment VARCHAR(16) NOT NULL,
    sentiment_confidence NUMERIC(5,4) NOT NULL,
    sentiment_summary TEXT NOT NULL,
    topics JSONB NOT NULL,
    aspects JSONB NOT NULL,
    issues JSONB NOT NULL,
    positive_highlights JSONB NOT NULL,
    negative_highlights JSONB NOT NULL,
    improvement_opportunities JSONB NOT NULL,
    ai_provider VARCHAR(64) NOT NULL,
    ai_model VARCHAR(128) NOT NULL,
    analyzed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_feedback_analysis_id_business UNIQUE (id, business_id),
    CONSTRAINT fk_feedback_analysis_feedback_business
        FOREIGN KEY (feedback_id, business_id)
        REFERENCES feedback(id, business_id) ON DELETE RESTRICT,
    CONSTRAINT ck_feedback_analysis_sentiment
        CHECK (sentiment IN ('POSITIVE', 'NEUTRAL', 'NEGATIVE', 'MIXED')),
    CONSTRAINT ck_feedback_analysis_confidence
        CHECK (sentiment_confidence BETWEEN 0 AND 1)
);
CREATE INDEX ix_feedback_analysis_business_sentiment
    ON feedback_analysis(business_id, sentiment, analyzed_at DESC);

CREATE TABLE insight_summaries (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE RESTRICT,
    period_start TIMESTAMPTZ NOT NULL,
    period_end TIMESTAMPTZ NOT NULL,
    summary_text TEXT NOT NULL,
    positive_highlights JSONB NOT NULL,
    negative_highlights JSONB NOT NULL,
    recurring_issues JSONB NOT NULL,
    recommendations JSONB NOT NULL,
    feedback_count INTEGER NOT NULL,
    latest_feedback_at TIMESTAMPTZ,
    ai_provider VARCHAR(64) NOT NULL,
    ai_model VARCHAR(128) NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_insight_summaries_period CHECK (period_end > period_start),
    CONSTRAINT ck_insight_summaries_count CHECK (feedback_count >= 0),
    CONSTRAINT uk_insight_summaries_business_period UNIQUE (business_id, period_start, period_end)
);
CREATE INDEX ix_insight_summaries_business_generated
    ON insight_summaries(business_id, generated_at DESC);

CREATE TABLE google_reviews (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE RESTRICT,
    source_review_id VARCHAR(255) NOT NULL,
    reviewer_display_name VARCHAR(255),
    rating INTEGER NOT NULL,
    review_text TEXT,
    reviewed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_google_reviews_business_source UNIQUE (business_id, source_review_id),
    CONSTRAINT uk_google_reviews_id_business UNIQUE (id, business_id),
    CONSTRAINT ck_google_reviews_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT ck_google_reviews_text_length CHECK (review_text IS NULL OR length(review_text) <= 10000)
);
CREATE INDEX ix_google_reviews_business_reviewed
    ON google_reviews(business_id, reviewed_at DESC);

CREATE TABLE review_response_drafts (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    google_review_id UUID NOT NULL,
    generated_text TEXT NOT NULL,
    response_text TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    tone VARCHAR(16) NOT NULL,
    ai_provider VARCHAR(64) NOT NULL,
    ai_model VARCHAR(128) NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    approved_at TIMESTAMPTZ,
    approved_by UUID REFERENCES users(id) ON DELETE RESTRICT,
    published_at TIMESTAMPTZ,
    rejection_reason TEXT,
    CONSTRAINT fk_review_drafts_review_business
        FOREIGN KEY (google_review_id, business_id)
        REFERENCES google_reviews(id, business_id) ON DELETE RESTRICT,
    CONSTRAINT fk_review_drafts_business FOREIGN KEY (business_id)
        REFERENCES businesses(id) ON DELETE RESTRICT,
    CONSTRAINT ck_review_drafts_status
        CHECK (status IN ('GENERATED', 'EDITED', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_review_drafts_tone
        CHECK (tone IN ('PROFESSIONAL', 'FRIENDLY', 'WARM', 'FORMAL')),
    CONSTRAINT ck_review_drafts_response_length
        CHECK (length(btrim(response_text)) BETWEEN 1 AND 2000),
    CONSTRAINT ck_review_drafts_not_published CHECK (published_at IS NULL)
);
CREATE INDEX ix_review_drafts_business_generated
    ON review_response_drafts(business_id, generated_at DESC);
CREATE INDEX ix_review_drafts_review_generated
    ON review_response_drafts(google_review_id, generated_at DESC);
