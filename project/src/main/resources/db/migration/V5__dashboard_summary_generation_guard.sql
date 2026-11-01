ALTER TABLE insight_summaries
    ADD COLUMN analyzed_feedback_count INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_insight_summaries_analyzed_count CHECK (analyzed_feedback_count >= 0);

CREATE TABLE insight_summary_generation_locks (
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    period_start TIMESTAMPTZ NOT NULL,
    period_end TIMESTAMPTZ NOT NULL,
    lock_token UUID NOT NULL,
    locked_until TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (business_id, period_start, period_end),
    CONSTRAINT ck_insight_summary_generation_period CHECK (period_end > period_start)
);
