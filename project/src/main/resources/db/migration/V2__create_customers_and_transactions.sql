CREATE TABLE customers (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE RESTRICT,
    name VARCHAR(120) NOT NULL,
    phone_encrypted TEXT NOT NULL,
    phone_lookup_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_customers_business_phone UNIQUE (business_id, phone_lookup_hash),
    CONSTRAINT uk_customers_id_business UNIQUE (id, business_id)
);

CREATE INDEX ix_customers_business_created ON customers(business_id, created_at DESC);
CREATE INDEX ix_customers_business_name ON customers(business_id, lower(name));

CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE RESTRICT,
    customer_id UUID NOT NULL,
    reference_code VARCHAR(100),
    status VARCHAR(32) NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_transactions_customer_business
        FOREIGN KEY (customer_id, business_id)
        REFERENCES customers(id, business_id) ON DELETE RESTRICT,
    CONSTRAINT ck_transactions_status CHECK (status = 'COMPLETED')
);

CREATE INDEX ix_transactions_business_completed
    ON transactions(business_id, completed_at DESC);
CREATE INDEX ix_transactions_customer_completed
    ON transactions(customer_id, completed_at DESC);
CREATE UNIQUE INDEX uk_transactions_business_reference
    ON transactions(business_id, reference_code)
    WHERE reference_code IS NOT NULL;
