ALTER TABLE transactions
    ADD CONSTRAINT uk_transactions_id_business UNIQUE (id, business_id);

CREATE TABLE feedback_questions (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE RESTRICT,
    question_text TEXT NOT NULL,
    question_type VARCHAR(16) NOT NULL,
    is_required BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_feedback_questions_type CHECK (question_type IN ('TEXT', 'RATING', 'YES_NO')),
    CONSTRAINT ck_feedback_questions_order CHECK (display_order >= 0),
    CONSTRAINT ck_feedback_questions_text CHECK (length(btrim(question_text)) BETWEEN 1 AND 1000),
    CONSTRAINT uk_feedback_questions_id_business UNIQUE (id, business_id)
);

CREATE INDEX ix_feedback_questions_business_order
    ON feedback_questions(business_id, is_active, display_order, id);

CREATE TABLE feedback_requests (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE RESTRICT,
    transaction_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    request_token_hash VARCHAR(64) NOT NULL UNIQUE,
    delivery_event_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    scheduled_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    failure_reason TEXT,
    provider_message_id VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_feedback_requests_transaction UNIQUE (transaction_id),
    CONSTRAINT uk_feedback_requests_id_business UNIQUE (id, business_id),
    CONSTRAINT fk_feedback_requests_transaction_business
        FOREIGN KEY (transaction_id, business_id)
        REFERENCES transactions(id, business_id) ON DELETE RESTRICT,
    CONSTRAINT fk_feedback_requests_customer_business
        FOREIGN KEY (customer_id, business_id)
        REFERENCES customers(id, business_id) ON DELETE RESTRICT,
    CONSTRAINT ck_feedback_requests_status CHECK
        (status IN ('PENDING', 'SCHEDULED', 'SENT', 'DELIVERED', 'FAILED', 'COMPLETED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ck_feedback_requests_expiry CHECK (expires_at > created_at)
);

CREATE INDEX ix_feedback_requests_business_created
    ON feedback_requests(business_id, created_at DESC);
CREATE INDEX ix_feedback_requests_business_status_scheduled
    ON feedback_requests(business_id, status, scheduled_at);
CREATE INDEX ix_feedback_requests_customer ON feedback_requests(customer_id);
CREATE INDEX ix_feedback_requests_expires ON feedback_requests(status, expires_at);

CREATE TABLE feedback_conversations (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE RESTRICT,
    feedback_request_id UUID NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_feedback_conversations_id_business UNIQUE (id, business_id),
    CONSTRAINT fk_feedback_conversations_request_business
        FOREIGN KEY (feedback_request_id, business_id)
        REFERENCES feedback_requests(id, business_id) ON DELETE RESTRICT,
    CONSTRAINT ck_feedback_conversations_status CHECK
        (status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'ABANDONED', 'EXPIRED'))
);

CREATE INDEX ix_feedback_conversations_business_status
    ON feedback_conversations(business_id, status, created_at DESC);

CREATE TABLE conversation_messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES feedback_conversations(id) ON DELETE RESTRICT,
    sender_type VARCHAR(16) NOT NULL,
    message_type VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_conversation_message_sender CHECK (sender_type IN ('CUSTOMER', 'SYSTEM', 'AI')),
    CONSTRAINT ck_conversation_message_type CHECK
        (message_type IN ('TEXT', 'QUESTION', 'ANSWER', 'RATING', 'SYSTEM_EVENT')),
    CONSTRAINT ck_conversation_message_content CHECK (length(content) BETWEEN 1 AND 10000)
);

CREATE INDEX ix_conversation_messages_conversation_created
    ON conversation_messages(conversation_id, created_at, id);

CREATE TABLE feedback (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE RESTRICT,
    transaction_id UUID NOT NULL UNIQUE,
    customer_id UUID NOT NULL,
    feedback_request_id UUID NOT NULL UNIQUE,
    conversation_id UUID NOT NULL UNIQUE,
    rating INTEGER NOT NULL,
    comment TEXT,
    submitted_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_feedback_id_business UNIQUE (id, business_id),
    CONSTRAINT fk_feedback_transaction_business
        FOREIGN KEY (transaction_id, business_id)
        REFERENCES transactions(id, business_id) ON DELETE RESTRICT,
    CONSTRAINT fk_feedback_customer_business
        FOREIGN KEY (customer_id, business_id)
        REFERENCES customers(id, business_id) ON DELETE RESTRICT,
    CONSTRAINT fk_feedback_request_business
        FOREIGN KEY (feedback_request_id, business_id)
        REFERENCES feedback_requests(id, business_id) ON DELETE RESTRICT,
    CONSTRAINT fk_feedback_conversation_business
        FOREIGN KEY (conversation_id, business_id)
        REFERENCES feedback_conversations(id, business_id) ON DELETE RESTRICT,
    CONSTRAINT ck_feedback_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT ck_feedback_comment_length CHECK (comment IS NULL OR length(comment) <= 5000)
);

CREATE INDEX ix_feedback_business_submitted
    ON feedback(business_id, submitted_at DESC);
CREATE INDEX ix_feedback_customer ON feedback(customer_id);

CREATE TABLE feedback_answers (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    feedback_id UUID NOT NULL,
    question_id UUID NOT NULL,
    answer_text TEXT,
    answer_rating INTEGER,
    answer_boolean BOOLEAN,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_feedback_answers_feedback_question UNIQUE (feedback_id, question_id),
    CONSTRAINT fk_feedback_answers_feedback_business
        FOREIGN KEY (feedback_id, business_id)
        REFERENCES feedback(id, business_id) ON DELETE RESTRICT,
    CONSTRAINT fk_feedback_answers_question_business
        FOREIGN KEY (question_id, business_id)
        REFERENCES feedback_questions(id, business_id) ON DELETE RESTRICT,
    CONSTRAINT ck_feedback_answers_value CHECK (
        (answer_text IS NOT NULL AND answer_rating IS NULL AND answer_boolean IS NULL)
        OR (answer_text IS NULL AND answer_rating BETWEEN 1 AND 5 AND answer_boolean IS NULL)
        OR (answer_text IS NULL AND answer_rating IS NULL AND answer_boolean IS NOT NULL)
    )
);

CREATE INDEX ix_feedback_answers_question ON feedback_answers(question_id);

CREATE TABLE feedback_outbox (
    id UUID PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    last_error TEXT,
    CONSTRAINT ck_feedback_outbox_status CHECK (status IN ('PENDING', 'PROCESSING', 'PROCESSED', 'FAILED')),
    CONSTRAINT ck_feedback_outbox_attempts CHECK (attempt_count >= 0)
);

CREATE INDEX ix_feedback_outbox_ready
    ON feedback_outbox(status, next_attempt_at, created_at);

CREATE TABLE feedback_webhook_events (
    event_id VARCHAR(128) PRIMARY KEY,
    feedback_request_id UUID NOT NULL REFERENCES feedback_requests(id) ON DELETE RESTRICT,
    provider_message_id VARCHAR(255),
    received_at TIMESTAMPTZ NOT NULL
);
